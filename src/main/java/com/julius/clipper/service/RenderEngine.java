package com.julius.clipper.service;

import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RenderEngine {
    private static final Logger log = LoggerFactory.getLogger(RenderEngine.class);

    private final RenderArtifactRepository artifactRepository;
    private final SubtitleCompiler subtitleCompiler;
    private final Renderer renderer;
    private final StorageClient storageClient;
    private final DistributedLockManager lockManager;
    private final JobClipRepository jobClipRepository;

    private final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();

    public RenderEngine(
            RenderArtifactRepository artifactRepository,
            SubtitleCompiler subtitleCompiler,
            Renderer renderer,
            StorageClient storageClient,
            DistributedLockManager lockManager,
            JobClipRepository jobClipRepository) {
        this.artifactRepository = artifactRepository;
        this.subtitleCompiler = subtitleCompiler;
        this.renderer = renderer;
        this.storageClient = storageClient;
        this.lockManager = lockManager;
        this.jobClipRepository = jobClipRepository;
    }

    public RenderArtifact dispatchRender(ClipVersion version, RenderProfile profile) throws Exception {
        String hash = computeRenderHash(version, profile);
        log.info("Dispatching render job for hash: {}", hash);

        // 1. Check if an identical render already exists and is completed
        Optional<RenderArtifact> cached = artifactRepository.findFirstByRenderHashAndStatusOrderByCreatedAtDesc(hash, "COMPLETED");
        if (cached.isPresent()) {
            log.info("Found cached render artifact: {}", cached.get().getId());
            return cached.get();
        }

        // 2. Lock check to handle concurrent rendering requests
        String lockKey = "lock:render:" + hash;
        String ownerId = "render-worker-" + hash;
        boolean acquired = lockManager.acquireLock(lockKey, ownerId, 300); // 5 minutes lease

        if (!acquired) {
            log.info("Render lock denied. Subscribing to active rendering job for hash: {}", hash);
            return artifactRepository.findFirstByRenderHashAndStatusOrderByCreatedAtDesc(hash, "RENDERING")
                    .orElseGet(() -> {
                        // Fallback in case of race condition: create pending artifact
                        RenderArtifact artifact = RenderArtifact.builder()
                                .version(version)
                                .profile(profile)
                                .status("QUEUED")
                                .renderHash(hash)
                                .build();
                        return artifactRepository.save(artifact);
                    });
        }

        // Lock acquired, we are the owner. Initialize render artifact record
        RenderArtifact artifact = RenderArtifact.builder()
                .version(version)
                .profile(profile)
                .status("PREPARING")
                .renderHash(hash)
                .build();
        artifact = artifactRepository.save(artifact);

        final RenderArtifact activeArtifact = artifact;

        // Perform transcoding asynchronously
        CompletableFutureRunner(activeArtifact, version, profile, lockKey);

        return activeArtifact;
    }

    private void CompletableFutureRunner(RenderArtifact artifact, ClipVersion version, RenderProfile profile, String lockKey) {
        Thread.startVirtualThread(() -> {
            File subFile = null;
            File tempOut = null;
            try {
                // Find input media path from parent JobClip details
                String clipId = version.getSession().getClipId();
                JobClip jobClip = jobClipRepository.findById(clipId)
                        .orElseThrow(() -> new NoSuchElementException("Parent JobClip not found: " + clipId));

                // Standard local downloads cache lookup
                String localInputPath = "data/jobs/" + jobClip.getJobId() + "/clips/" + jobClip.getFilename();
                File localFile = new File(localInputPath);
                if (!localFile.exists()) {
                    // Try parent source download path if fragment is missing
                    localInputPath = "data/jobs/" + jobClip.getJobId() + "/source.mp4";
                }

                // Parse segment timings from timeline JSON
                double startTime = 0.0;
                double endTime = jobClip.getDurationSeconds();

                // Generate subtitle compiled script
                String assContent = subtitleCompiler.compile(version.getStylePreset(), version.getTimelineStateJson());
                subFile = File.createTempFile("julius_sub_" + artifact.getId(), ".ass");
                try (FileWriter writer = new FileWriter(subFile, StandardCharsets.UTF_8)) {
                    writer.write(assContent);
                }

                // Update status to RENDERING
                artifact.setStatus("RENDERING");
                artifactRepository.save(artifact);

                String outFilename = "rendered_" + UUID.randomUUID().toString().substring(0, 8) + ".mp4";
                tempOut = new File(subFile.getParentFile(), outFilename);

                // Run FFmpeg cut & subtitle burn operation
                String renderedPath = renderer.render(
                        localInputPath,
                        startTime,
                        endTime,
                        profile,
                        subFile,
                        outFilename
                ).get(); // Synchronous block within the virtual thread

                File renderedFile = new File(renderedPath);

                // Upload rendered asset to StorageClient
                artifact.setStatus("UPLOADING");
                artifactRepository.save(artifact);

                String storageKey = "jobs/" + jobClip.getJobId() + "/rendered/" + outFilename;
                try (InputStream stream = new FileInputStream(renderedFile)) {
                    StoredObject obj = storageClient.upload(new UploadRequest(
                            storageKey,
                            stream,
                            renderedFile.length(),
                            "video/mp4",
                            null,
                            Map.of("version_id", version.getId())
                      ));
                    
                    artifact.setStatus("COMPLETED");
                    artifact.setStorageKey(storageKey);
                    artifact.setUrl(obj.publicUri() != null ? obj.publicUri() : "/data/" + storageKey);
                    artifact.setSizeBytes(renderedFile.length());
                    artifact.setDurationSeconds(endTime - startTime);
                    artifact.setCompletedAt(LocalDateTime.now());
                    artifactRepository.save(artifact);
                }

                log.info("Render artifact complete: {}", artifact.getId());
            } catch (Exception e) {
                log.error("Asynchronous rendering process failed: {}", e.getMessage());
                artifact.setStatus("FAILED");
                artifact.setErrorMessage(e.getMessage());
                artifactRepository.save(artifact);
            } finally {
                // Cleanup temp files
                if (subFile != null && subFile.exists()) subFile.delete();
                if (tempOut != null && tempOut.exists()) tempOut.delete();
                // Release rendering lock
                lockManager.releaseLock(lockKey, "render-worker-" + artifact.getRenderHash());
                
                // Notify any concurrent listeners waiting for completion
                triggerListeners(artifact.getRenderHash());
            }
        });
    }

    public void addListener(String hash, Runnable callback) {
        listeners.computeIfAbsent(hash, k -> new ArrayList<>()).add(callback);
    }

    private void triggerListeners(String hash) {
        List<Runnable> list = listeners.remove(hash);
        if (list != null) {
            for (Runnable r : list) {
                try { r.run(); } catch (Exception ignored) {}
            }
        }
    }

    private String computeRenderHash(ClipVersion version, RenderProfile profile) throws Exception {
        String raw = String.format("%s_%s_%s_%s",
                version.getId(),
                profile.getId(),
                version.getStylePreset().getId(),
                version.getTimelineStateJson()
        );
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
