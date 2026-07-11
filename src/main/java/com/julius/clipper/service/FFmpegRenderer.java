package com.julius.clipper.service;

import com.julius.clipper.domain.RenderProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class FFmpegRenderer implements Renderer {
    private static final Logger log = LoggerFactory.getLogger(FFmpegRenderer.class);
    private final String outputDir;

    public FFmpegRenderer(com.julius.clipper.config.properties.WorkspaceProperties workspaceProperties) {
        this.outputDir = workspaceProperties.cutDir();
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public CompletableFuture<String> render(
            String inputPath,
            double startTime,
            double endTime,
            RenderProfile profile,
            File subtitleFile,
            String outputFilename) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                File inputFile = new File(inputPath);
                if (!inputFile.exists()) {
                    throw new FileNotFoundException("Input video file not found: " + inputPath);
                }

                double duration = endTime - startTime;
                if (duration <= 0.0) {
                    throw new IllegalArgumentException("Invalid cut duration: " + duration);
                }

                Path destinationFolder = Paths.get(outputDir);
                if (!Files.exists(destinationFolder)) {
                    Files.createDirectories(destinationFolder);
                }

                String outputPath = destinationFolder.resolve(outputFilename).toAbsolutePath().toString();
                log.info("Starting FFmpeg render. Output path: {}", outputPath);

                // Format subtitles path properly for FFmpeg filter parsing (handling Windows slashes)
                String subPath = subtitleFile.getAbsolutePath();
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    subPath = subPath.replace("\\", "/").replace(":", "\\:");
                }

                // Build layout scaling and subtitle burning filters
                String filterComplex = String.format("scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d,subtitles='%s'",
                        profile.getWidth(), profile.getHeight(),
                        profile.getWidth(), profile.getHeight(),
                        subPath);

                List<String> command = new ArrayList<>(Arrays.asList(
                        "ffmpeg",
                        "-y",
                        "-ss", String.valueOf(startTime),
                        "-i", inputPath,
                        "-t", String.valueOf(duration),
                        "-vf", filterComplex,
                        "-c:v", "libx264",
                        "-preset", "ultrafast",
                        "-crf", "23",
                        "-c:a", "aac",
                        "-b:a", String.valueOf(profile.getAudioBitrateKbps()) + "k",
                        "-movflags", "+faststart",
                        outputPath
                ));

                log.debug("Executing FFmpeg render command: {}", String.join(" ", command));

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
                        log.error("Error reading FFmpeg stream: {}", e.getMessage());
                    }
                });
                streamConsumer.setDaemon(true);
                streamConsumer.start();

                long timeoutSeconds = 600;
                boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

                if (!completed) {
                    process.destroyForcibly();
                    throw new RuntimeException("FFmpeg render command timed out after " + timeoutSeconds + " seconds.");
                }

                int exitValue = process.exitValue();
                if (exitValue != 0) {
                    throw new RuntimeException("FFmpeg render failed with exit code " + exitValue + ". Logs: " + processOutput.toString());
                }

                File outputFile = new File(outputPath);
                if (!outputFile.exists() || outputFile.length() < 1024) {
                    throw new RuntimeException("Generated export file is corrupt or missing: " + outputPath);
                }

                log.info("FFmpeg render completed successfully: {}", outputPath);
                return outputPath;
            } catch (Exception e) {
                log.error("FFmpeg render failed with exception: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}
