package com.julius.clipper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VideoEditor {

    private static final Logger log = LoggerFactory.getLogger(VideoEditor.class);

    private final String outputDir;

    public VideoEditor(@Value("${clipper.cut.dir:data/temp/fragments}") String outputDir) {
        this.outputDir = outputDir;
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public String cutVideo(String inputPath, double startTime, double endTime, String outputFilename) throws Exception {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("Input video file not found for editing: " + inputPath);
        }

        double duration = endTime - startTime;
        if (duration <= 0.0) {
            throw new IllegalArgumentException("Invalid cut timestamps: Start time must be less than end time. Got start=" + startTime + ", end=" + endTime);
        }

        if (outputFilename == null || outputFilename.isBlank()) {
            String baseName = inputFile.getName();
            int extIdx = baseName.lastIndexOf('.');
            if (extIdx > 0) {
                baseName = baseName.substring(0, extIdx);
            }
            outputFilename = baseName + "_cut_" + (int) startTime + "_" + (int) endTime + ".mp4";
        }

        Path destinationFolder = Paths.get(outputDir);
        if (!Files.exists(destinationFolder)) {
            Files.createDirectories(destinationFolder);
        }

        String outputPath = destinationFolder.resolve(outputFilename).toAbsolutePath().toString();
        log.info("Starting FFmpeg video cut: {} from {}s to {}s (duration: {}s) -> {}", inputPath, startTime, endTime, duration, outputPath);

        // Build command executing frame-accurate cut using libx264 encoder, preserving optional audio map
        List<String> command = new ArrayList<>(Arrays.asList(
                "ffmpeg",
                "-y",
                "-ss", String.valueOf(startTime),
                "-i", inputPath,
                "-t", String.valueOf(duration),
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-crf", "23",
                "-c:a", "aac",
                "-b:a", "128k",
                "-map", "0:v:0",
                "-map", "0:a?",
                "-movflags", "+faststart",
                outputPath
        ));

        log.debug("Executing process builder command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Consume process input/error stream in a separate thread to prevent lockups
        StringBuilder processOutput = new StringBuilder();
        Thread streamConsumer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processOutput.append(line).append("\n");
                }
            } catch (IOException e) {
                log.error("Error reading FFmpeg stream output: {}", e.getMessage());
            }
        });
        
        streamConsumer.setDaemon(true);
        streamConsumer.start();

        // Calculate a reasonable timeout based on clip duration (min 120 seconds, max 600 seconds)
        long timeoutSeconds = Math.max(120, Math.min(600, (long) (duration * 5)));
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            try {
                streamConsumer.join(1000);
            } catch (InterruptedException ignored) {}
            throw new RuntimeException("FFmpeg cut command timed out after " + timeoutSeconds + " seconds.");
        }

        // Wait for thread to finish reading output
        try {
            streamConsumer.join(2000);
        } catch (InterruptedException ignored) {}

        int exitValue = process.exitValue();
        if (exitValue != 0) {
            String errorLogs = processOutput.toString();
            log.error("FFmpeg cutting process failed with exit code {}. Logs:\n{}", exitValue, errorLogs);
            throw new RuntimeException("FFmpeg cutting process failed with exit code " + exitValue + ". Logs: " + errorLogs);
        }

        File outputFile = new File(outputPath);
        if (!outputFile.exists() || outputFile.length() < 1024) {
            long size = outputFile.exists() ? outputFile.length() : 0;
            throw new RuntimeException("FFmpeg successfully exited, but generated file is corrupt or missing: " + size + " bytes.");
        }

        log.info("FFmpeg video cut successfully generated: {}", outputPath);
        return outputPath;
    }
}
