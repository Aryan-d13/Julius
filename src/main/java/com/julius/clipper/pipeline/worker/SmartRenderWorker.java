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
    private final String outputDir;

    public SmartRenderWorker(VideoEditor videoEditor,
                             @Value("${clipper.render.output.dir:data/jobs}") String outputDir) {
        this.videoEditor = videoEditor;
        this.outputDir = outputDir;
        new File(outputDir).mkdirs();
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
        File sourceVideoFile = new File(sourceVideoKey);
        if (!sourceVideoFile.exists()) {
            throw new FileNotFoundException("Input video file does not exist on disk: " + sourceVideoKey);
        }

        // Generate output clip filename
        String clipFilename = buildClipFilename(jobId, index, sourceTitle, templateRef);

        Path destClipsDir = Paths.get(outputDir, jobId, "clips");
        Files.createDirectories(destClipsDir);

        // Perform video cut via VideoEditor process
        String generatedFragmentPath = videoEditor.cutVideo(sourceVideoFile.getAbsolutePath(), start, end, clipFilename);
        
        Path destClipPath = destClipsDir.resolve(clipFilename);
        Files.move(Paths.get(generatedFragmentPath), destClipPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        File finalClipFile = destClipPath.toFile();

        // Size check validation (> 1KB)
        if (!finalClipFile.exists() || finalClipFile.length() < 1024) {
            long size = finalClipFile.exists() ? finalClipFile.length() : 0;
            throw new RuntimeException("Generated clip size check failed: output is " + size + " bytes (expected > 1024 bytes).");
        }

        // Probed duration check via ffprobe (> 5 seconds)
        double probedDuration = probeVideoDuration(finalClipFile.getAbsolutePath());
        if (probedDuration < 5.0) {
            throw new RuntimeException("Generated clip duration validation failed: output is " + probedDuration + " seconds (expected >= 5.0 seconds).");
        }
        if (probedDuration < duration * 0.5) {
            log.warn("Generated clip duration mismatch: expected ~{}s, got {}s for clip {}", duration, probedDuration, finalClipFile.getName());
        }

        Map<String, Object> outputPayload = new HashMap<>();
        outputPayload.put("index", index);
        outputPayload.put("filename", clipFilename);
        outputPayload.put("storage_key", destClipPath.toAbsolutePath().toString());
        outputPayload.put("url", "http://localhost:8080/data/jobs/" + jobId + "/clips/" + clipFilename);
        outputPayload.put("duration_seconds", probedDuration);
        outputPayload.put("size_bytes", finalClipFile.length());

        log.info("SmartRenderWorker completed successfully for clip #{}: {}", index, destClipPath);
        return outputPayload;
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
