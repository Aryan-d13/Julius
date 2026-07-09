package com.julius.clipper.pipeline.worker;

import com.julius.clipper.domain.Task;
import com.julius.clipper.pipeline.Worker;
import com.julius.clipper.service.VideoEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component("SMART_RENDERWorker")
public class SmartRenderWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(SmartRenderWorker.class);

    private final VideoEditor videoEditor;
    private final com.julius.clipper.service.StorageClient storageClient;

    public SmartRenderWorker(VideoEditor videoEditor,
                             com.julius.clipper.service.StorageClient storageClient) {
        this.videoEditor = videoEditor;
        this.storageClient = storageClient;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> process(Task task) throws Exception {
        String jobId = task.getJobId();
        int index = getIntValue(task.getPayload(), "index", 1);
        String sourceVideoKey = (String) task.getPayload().get("source_video_key");
        String sourceTitle = (String) task.getPayload().get("source_title");
        String templateRef = (String) task.getPayload().get("template_ref");

        Map<String, Object> timeWindow = (Map<String, Object>) task.getPayload().get("time_window");
        if (timeWindow == null) {
            throw new IllegalArgumentException("Time window specification is missing in the render payload.");
        }

        double start = getDoubleValue(timeWindow, "start", 0.0);
        double end = getDoubleValue(timeWindow, "end", 0.0);
        double duration = end - start;

        log.info("SmartRenderWorker starting clip cut #{}: Job {}, Window [{}s - {}s] (dur={}s)", index, jobId, start, end, duration);

        if (sourceVideoKey == null || sourceVideoKey.isBlank()) {
            throw new FileNotFoundException("Input video source key is unspecified in the payload.");
        }

        File tempSourceVideoFile = File.createTempFile("render_source_", ".mp4");
        tempSourceVideoFile.deleteOnExit();

        File tempCutFile = null;

        try {
            // Download the source video from cloud storage locally for processing
            try (OutputStream out = new FileOutputStream(tempSourceVideoFile)) {
                storageClient.download(sourceVideoKey, out);
            }

            // Generate output clip filename
            String clipFilename = buildClipFilename(jobId, index, sourceTitle, templateRef);

            // Perform video cut via VideoEditor process
            String generatedFragmentPath = videoEditor.cutVideo(tempSourceVideoFile.getAbsolutePath(), start, end, clipFilename);
            tempCutFile = new File(generatedFragmentPath);

            // Size check validation (> 1KB)
            if (!tempCutFile.exists() || tempCutFile.length() < 1024) {
                long size = tempCutFile.exists() ? tempCutFile.length() : 0;
                throw new RuntimeException("Generated clip size check failed: output is " + size + " bytes (expected > 1024 bytes).");
            }

            // Probed duration check via ffprobe (> 5 seconds)
            double probedDuration = probeVideoDuration(tempCutFile.getAbsolutePath());
            if (probedDuration < 5.0) {
                throw new RuntimeException("Generated clip duration validation failed: output is " + probedDuration + " seconds (expected >= 5.0 seconds).");
            }
            if (probedDuration < duration * 0.5) {
                log.warn("Generated clip duration mismatch: expected ~{}s, got {}s for clip {}", duration, probedDuration, tempCutFile.getName());
            }

            // Upload final clip to Storage
            String targetClipKey = com.julius.clipper.service.StorageKeyBuilder.jobClip(jobId, index, templateRef);
            log.info("Uploading final clip fragment to cloud storage: {}", targetClipKey);
            try (InputStream in = new FileInputStream(tempCutFile)) {
                storageClient.upload(new com.julius.clipper.service.UploadRequest(
                    targetClipKey,
                    in,
                    tempCutFile.length(),
                    "video/mp4",
                    null,
                    null
                ));
            }

            // Generate secure signed URL for public download link (expires in 24 hours)
            String signedUrl = storageClient.generateSignedUrl(targetClipKey, java.time.Duration.ofHours(24));

            Map<String, Object> outputPayload = new HashMap<>();
            outputPayload.put("index", index);
            outputPayload.put("filename", clipFilename);
            outputPayload.put("storage_key", targetClipKey);
            outputPayload.put("url", signedUrl);
            outputPayload.put("duration_seconds", probedDuration);
            outputPayload.put("size_bytes", tempCutFile.length());

            log.info("SmartRenderWorker completed successfully for clip #{}: {}, signedUrl={}", index, targetClipKey, signedUrl);
            return outputPayload;

        } finally {
            if (tempSourceVideoFile.exists()) {
                tempSourceVideoFile.delete();
            }
            if (tempCutFile != null && tempCutFile.exists()) {
                tempCutFile.delete();
            }
        }
    }

    private double probeVideoDuration(String videoPath) {
        try {
            List<String> command = Arrays.asList(
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    videoPath
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            if (completed && process.exitValue() == 0) {
                return Double.parseDouble(output.toString().trim());
            }
        } catch (Exception e) {
            log.warn("ffprobe duration probe failed: {}", e.getMessage());
        }
        return 0.0;
    }

    private String buildClipFilename(String jobId, int clipIndex, String sourceTitle, String templateRef) {
        String titleSlug = toSlug(sourceTitle != null ? sourceTitle : "untitled-source");
        String templateSlug = toSlug(templateRef != null ? templateRef.replace('/', '-') : "template");
        String shortJobId = jobId != null ? jobId.substring(0, Math.min(jobId.length(), 8)) : "job";
        return String.format("clip-%02d_%s_%s_%s.mp4", clipIndex, titleSlug, templateSlug, shortJobId);
    }

    private String toSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultVal) {
        if (map == null) return defaultVal;
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return defaultVal;
    }

    private double getDoubleValue(Map<String, Object> map, String key, double defaultVal) {
        if (map == null) return defaultVal;
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return defaultVal;
    }
}
