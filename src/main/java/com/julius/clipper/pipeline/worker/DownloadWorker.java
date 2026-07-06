package com.julius.clipper.pipeline.worker;

import com.julius.clipper.domain.Job;
import com.julius.clipper.domain.Task;
import com.julius.clipper.domain.dto.JobConfig;
import com.julius.clipper.pipeline.TaskType;
import com.julius.clipper.pipeline.Worker;
import com.julius.clipper.repository.JobRepository;
import com.julius.clipper.service.DistributedLockManager;
import com.julius.clipper.service.MediaConverter;
import com.julius.clipper.service.YouTubeDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("DOWNLOADWorker")
public class DownloadWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(DownloadWorker.class);

    private final YouTubeDownloader downloader;
    private final MediaConverter converter;
    private final JobRepository jobRepository;
    private final DistributedLockManager lockManager;

    public DownloadWorker(YouTubeDownloader downloader, MediaConverter converter, 
                          JobRepository jobRepository, DistributedLockManager lockManager) {
        this.downloader = downloader;
        this.converter = converter;
        this.jobRepository = jobRepository;
        this.lockManager = lockManager;
    }

    @Override
    public Map<String, Object> process(Task task) throws Exception {
        String url = (String) task.getPayload().get("url");
        String clipId = (String) task.getPayload().get("clip_id");
        String jobId = task.getJobId();

        boolean isVideoTask = (task.getType() == TaskType.DOWNLOAD_VIDEO);

        if (clipId == null || clipId.isBlank()) {
            clipId = extractYouTubeId(url);
            if (clipId == null) {
                clipId = UUID.nameUUIDFromBytes(url.getBytes()).toString().substring(0, 12);
            }
        }

        log.info("DownloadWorker starting: Job {}, clipId {}, mode={}", jobId, clipId, isVideoTask ? "video" : "audio");

        String sourceTitle = "Untitled Download Source";
        try {
            Map<String, Object> probeDetails = downloader.probeVideo(url);
            String resolvedId = (String) probeDetails.get("id");
            if (resolvedId != null && !resolvedId.equals(clipId)) {
                log.info("Updating clipId to canonical resolved ID: {} -> {}", clipId, resolvedId);
                clipId = resolvedId;
            }
            if (probeDetails.containsKey("title")) {
                sourceTitle = (String) probeDetails.get("title");
            } else if (probeDetails.containsKey("fulltitle")) {
                sourceTitle = (String) probeDetails.get("fulltitle");
            }

            if (jobId != null) {
                updateJobConfig(jobId, sourceTitle, clipId);
            }
        } catch (Exception e) {
            log.warn("Could not retrieve video metadata details for url {}: {}", url, e.getMessage());
        }

        // Distributed Redis locking instead of local locking
        String lockKey = "seone:lock:download:" + clipId;
        String ownerId = UUID.randomUUID().toString();
        boolean acquired = false;
        long startLockTime = System.currentTimeMillis();

        // Retry polling for up to 5 minutes to allow other workers to download
        while (System.currentTimeMillis() - startLockTime < 300000) {
            acquired = lockManager.acquireLock(lockKey, ownerId, 600);
            if (acquired) {
                break;
            }
            Thread.sleep(2000); // Check every 2 seconds
        }

        if (!acquired) {
            throw new RuntimeException("Failed to acquire distributed lock for download of clipId: " + clipId);
        }

        try {
            Map<String, Object> outputPayload = new HashMap<>();
            outputPayload.put("clip_id", clipId);

            if (isVideoTask) {
                String targetFilename = "source_video_" + clipId;
                String downloadedVideoPath = downloader.downloadVideo(url, targetFilename);
                outputPayload.put("video_key", downloadedVideoPath);
                log.info("Video download finished successfully: {}", downloadedVideoPath);
            } else {
                String targetFilename = "source_audio_" + clipId;
                String downloadedAudioPath = downloader.downloadAudio(url, targetFilename);
                String transcodedWavPath = converter.convertToWav(downloadedAudioPath);

                File rawAudioFile = new File(downloadedAudioPath);
                if (rawAudioFile.exists() && !downloadedAudioPath.endsWith(".wav")) {
                    rawAudioFile.delete();
                    log.debug("Cleaned up intermediate raw audio track file: {}", downloadedAudioPath);
                }

                outputPayload.put("storage_key", transcodedWavPath);
                log.info("Audio download and WAV transcoding finished: {}", transcodedWavPath);
            }

            return outputPayload;
        } finally {
            lockManager.releaseLock(lockKey, ownerId);
        }
    }

    private String extractYouTubeId(String url) {
        if (url == null) return null;
        Pattern pattern = Pattern.compile("(?:v=|\\/)([0-9A-Za-z_-]{11}).*");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void updateJobConfig(String jobId, String sourceTitle, String sourceClipId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            boolean isModified = false;
            JobConfig config = job.getConfig();
            if (config == null) {
                config = new JobConfig();
            }
            if (config.getSourceTitle() == null || !config.getSourceTitle().equals(sourceTitle)) {
                config.setSourceTitle(sourceTitle);
                isModified = true;
            }
            if (config.getSourceClipId() == null || !config.getSourceClipId().equals(sourceClipId)) {
                config.setSourceClipId(sourceClipId);
                isModified = true;
            }
            if (isModified) {
                job.setConfig(config);
                jobRepository.saveAndFlush(job);
                log.info("Job {} metadata updated. Title: '{}', ClipId: '{}'", jobId, sourceTitle, sourceClipId);
            }
        });
    }
}
