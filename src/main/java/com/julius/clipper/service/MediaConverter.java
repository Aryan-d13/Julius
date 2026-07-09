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
public class MediaConverter {

    private static final Logger log = LoggerFactory.getLogger(MediaConverter.class);

    private final String outputDir;

    public MediaConverter(com.julius.clipper.config.properties.WorkspaceProperties workspaceProperties) {
        this.outputDir = workspaceProperties.convertDir();
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public String convertToWav(String inputPath) throws Exception {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("Input media file not found for WAV conversion: " + inputPath);
        }

        String baseName = inputFile.getName();
        int extIdx = baseName.lastIndexOf('.');
        if (extIdx > 0) {
            baseName = baseName.substring(0, extIdx);
        }

        Path destinationFolder = Paths.get(outputDir);
        if (!Files.exists(destinationFolder)) {
            Files.createDirectories(destinationFolder);
        }

        String outputPath = destinationFolder.resolve(baseName + ".wav").toAbsolutePath().toString();
        log.info("Starting audio extraction and conversion to WAV format: {} -> {}", inputPath, outputPath);

        // FFmpeg command config: -ar 16000 (16kHz), -ac 1 (mono channel), -c:a pcm_s16le (16-bit PCM WAV)
        List<String> command = new ArrayList<>(Arrays.asList(
                "ffmpeg",
                "-y",
                "-nostdin",
                "-loglevel", "error",
                "-i", inputPath,
                "-ar", "16000",
                "-ac", "1",
                "-c:a", "pcm_s16le",
                outputPath
        ));

        log.debug("Executing FFmpeg converter process: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder processOutput = new StringBuilder();
        Thread streamConsumer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processOutput.append(line).append("\n");
                }
            } catch (IOException e) {
                log.error("Error reading FFmpeg converter output stream: {}", e.getMessage());
            }
        });

        streamConsumer.setDaemon(true);
        streamConsumer.start();

        // 3-minute conversion timeout threshold
        boolean completed = process.waitFor(180, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            try {
                streamConsumer.join(1000);
            } catch (InterruptedException ignored) {}
            throw new RuntimeException("FFmpeg converter process timed out after 180 seconds.");
        }

        try {
            streamConsumer.join(2000);
        } catch (InterruptedException ignored) {}

        int exitValue = process.exitValue();
        if (exitValue != 0) {
            String errorLogs = processOutput.toString();
            log.error("FFmpeg audio conversion failed with exit code {}. Logs:\n{}", exitValue, errorLogs);
            throw new RuntimeException("FFmpeg audio conversion failed with exit code " + exitValue + ". Logs: " + errorLogs);
        }

        File outputFile = new File(outputPath);
        if (!outputFile.exists() || outputFile.length() < 1024) {
            long size = outputFile.exists() ? outputFile.length() : 0;
            throw new RuntimeException("FFmpeg exited successfully, but the transcoded WAV file is corrupt or missing: " + size + " bytes.");
        }

        log.info("Audio track transcoded successfully to WAV format: {}", outputPath);
        return outputPath;
    }
}
