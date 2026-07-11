package com.julius.clipper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class SpriteGenerator {
    private static final Logger log = LoggerFactory.getLogger(SpriteGenerator.class);
    private final String outputDir;

    public SpriteGenerator(com.julius.clipper.config.properties.WorkspaceProperties workspaceProperties) {
        this.outputDir = workspaceProperties.convertDir();
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public File generateSpriteSheet(File videoFile, double durationSeconds, double intervalSeconds) {
        log.info("Generating timeline sprite sheet from video: {} (duration: {}s)", videoFile.getName(), durationSeconds);
        try {
            Path destinationFolder = Paths.get(outputDir);
            if (!Files.exists(destinationFolder)) {
                Files.createDirectories(destinationFolder);
            }

            File spriteFile = File.createTempFile("julius_sprite_", ".png", destinationFolder.toFile());
            
            // Grid calculations: standard 10 columns strip
            int cols = 10;
            int totalFrames = (int) Math.ceil(durationSeconds / intervalSeconds);
            int rows = (int) Math.ceil((double) totalFrames / cols);
            if (rows <= 0) rows = 1;

            // Compile FFmpeg command using fps, scale and tile filters
            String filterComplex = String.format("fps=1/%f,scale=160:90,tile=%dx%d", intervalSeconds, cols, rows);

            List<String> command = new ArrayList<>(Arrays.asList(
                    "ffmpeg",
                    "-y",
                    "-nostdin",
                    "-loglevel", "error",
                    "-i", videoFile.getAbsolutePath(),
                    "-vf", filterComplex,
                    "-frames:v", "1",
                    spriteFile.getAbsolutePath()
            ));

            log.debug("Executing FFmpeg sprite tiling process: {}", String.join(" ", command));

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
                    log.error("Error reading FFmpeg sprite process output: {}", e.getMessage());
                }
            });
            streamConsumer.setDaemon(true);
            streamConsumer.start();

            boolean completed = process.waitFor(180, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("FFmpeg sprite generator timed out.");
            }

            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new RuntimeException("FFmpeg sprite generator failed with code " + exitValue + ". Logs: " + processOutput.toString());
            }

            if (!spriteFile.exists() || spriteFile.length() < 100) {
                throw new RuntimeException("Generated sprite sheet is empty or corrupt.");
            }

            log.info("Sprite sheet generated successfully: {}", spriteFile.getName());
            return spriteFile;
        } catch (Exception e) {
            log.error("Failed to generate timeline sprite sheet: {}", e.getMessage(), e);
            return null;
        }
    }
}
