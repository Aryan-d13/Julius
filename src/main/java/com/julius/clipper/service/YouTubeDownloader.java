package com.julius.clipper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class YouTubeDownloader {

    private static final Logger log = LoggerFactory.getLogger(YouTubeDownloader.class);

    private final String outputDir;
    private final String cookiesPath;
    private final String ytdlpFormat;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YouTubeDownloader(com.julius.clipper.config.properties.DownloadProperties downloadProperties) {
        this.outputDir = downloadProperties.dir();
        this.cookiesPath = downloadProperties.cookiesPath();
        this.ytdlpFormat = downloadProperties.format();

        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public Map<String, Object> probeVideo(String url) throws Exception {
        log.info("Probing video metadata for: {}", url);
        List<String> command = new ArrayList<>(Arrays.asList(
                "yt-dlp",
                "--dump-json",
                "--no-playlist",
                "--force-ipv4",
                "--socket-timeout", "30",
                "--extractor-args", "youtube:player_client=android,ios,tv"
        ));

        addCookiesIfAvailable(command);
        command.add(url);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        StringBuilder stdoutBuffer = new StringBuilder();
        StringBuilder stderrBuffer = new StringBuilder();

        Thread stdoutConsumer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutBuffer.append(line);
                }
            } catch (IOException e) {
                log.error("Error reading yt-dlp probe stdout: {}", e.getMessage());
            }
        });

        Thread stderrConsumer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrBuffer.append(line).append("\n");
                }
            } catch (IOException e) {
                log.error("Error reading yt-dlp probe stderr: {}", e.getMessage());
            }
        });

        stdoutConsumer.start();
        stderrConsumer.start();

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            stdoutConsumer.join(1000);
            stderrConsumer.join(1000);
            throw new RuntimeException("yt-dlp metadata probe timed out after 60 seconds.");
        }

        stdoutConsumer.join(2000);
        stderrConsumer.join(2000);

        int exitValue = process.exitValue();
        if (exitValue != 0) {
            String errorMsg = stderrBuffer.toString();
            log.error("yt-dlp metadata probe failed with exit code {}. Logs:\n{}", exitValue, errorMsg);
            throw new RuntimeException("yt-dlp metadata probe failed: " + errorMsg);
        }

        String rawJson = stdoutBuffer.toString().trim();
        if (rawJson.isEmpty()) {
            throw new IllegalStateException("yt-dlp metadata probe successfully exited, but returned empty stdout.");
        }

        return objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
    }

    public String downloadAudio(String url, String filename) throws Exception {
        String outputPattern = Paths.get(outputDir, filename + ".%(ext)s").toString();
        log.info("Downloading audio track for: {} -> {}", url, outputPattern);

        List<String> command = new ArrayList<>(Arrays.asList(
                "yt-dlp",
                "-f", "bestaudio/bestaudio*/best",
                "--no-playlist",
                "--force-ipv4",
                "--socket-timeout", "30",
                "--extractor-args", "youtube:player_client=android,ios,tv",
                "-o", outputPattern
        ));

        addCookiesIfAvailable(command);
        command.add(url);

        executeDownloadProcess(command, 300);

        return resolveDownloadedFilePath(filename, Arrays.asList(".mp3", ".m4a", ".webm", ".opus", ".ogg", ".wav", ".aac"));
    }

    public String downloadVideo(String url, String filename) throws Exception {
        String outputPattern = Paths.get(outputDir, filename + ".%(ext)s").toString();
        log.info("Downloading video track for: {} -> {}", url, outputPattern);

        List<String> command = new ArrayList<>(Arrays.asList(
                "yt-dlp",
                "-f", ytdlpFormat,
                "--merge-output-format", "mp4",
                "--no-playlist",
                "--force-ipv4",
                "--socket-timeout", "30",
                "--extractor-args", "youtube:player_client=android,ios,tv",
                "-o", outputPattern
        ));

        addCookiesIfAvailable(command);
        command.add(url);

        executeDownloadProcess(command, 600);

        return resolveDownloadedFilePath(filename, Collections.singletonList(".mp4"));
    }

    private void addCookiesIfAvailable(List<String> command) {
        if (cookiesPath != null && !cookiesPath.isBlank()) {
            File cookieFile = new File(cookiesPath);
            if (cookieFile.exists() && cookieFile.isFile()) {
                command.add("--cookiefile");
                command.add(cookiesPath);
                log.debug("Using local cookies path parameter: {}", cookiesPath);
            } else {
                log.warn("Configured cookies path exists in properties but is invalid or missing on disk: {}", cookiesPath);
            }
        }
    }

    private void executeDownloadProcess(List<String> command, int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder logBuffer = new StringBuilder();
        Thread streamConsumer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logBuffer.append(line).append("\n");
                }
            } catch (IOException e) {
                log.error("Error reading yt-dlp process stdout: {}", e.getMessage());
            }
        });

        streamConsumer.setDaemon(true);
        streamConsumer.start();

        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            try {
                streamConsumer.join(1000);
            } catch (InterruptedException ignored) {}
            throw new RuntimeException("yt-dlp download timed out after " + timeoutSeconds + " seconds.");
        }

        try {
            streamConsumer.join(2000);
        } catch (InterruptedException ignored) {}

        int exitValue = process.exitValue();
        if (exitValue != 0) {
            String errorLogs = logBuffer.toString();
            log.error("yt-dlp download execution failed with exit code {}. Logs:\n{}", exitValue, errorLogs);
            throw new RuntimeException("yt-dlp download execution failed: " + errorLogs);
        }
    }

    private String resolveDownloadedFilePath(String filename, List<String> extensions) throws Exception {
        Path searchDirectory = Paths.get(outputDir);

        for (String ext : extensions) {
            Path expectedPath = searchDirectory.resolve(filename + ext);
            if (Files.exists(expectedPath)) {
                return expectedPath.toAbsolutePath().toString();
            }
        }

        // Fallback matching files that start with filename prefix
        List<Path> matchingFiles = Files.list(searchDirectory)
                .filter(p -> p.getFileName().toString().startsWith(filename))
                .collect(Collectors.toList());

        if (matchingFiles.isEmpty()) {
            throw new FileNotFoundException("yt-dlp process successfully executed, but no downloaded file matching " + filename + " was found in " + outputDir);
        }

        // Filter and sort to find complete non-temp downloads
        matchingFiles.sort((a, b) -> {
            String nameA = a.toString().toLowerCase();
            String nameB = b.toString().toLowerCase();
            if (nameA.endsWith(".part") && !nameB.endsWith(".part")) return 1;
            if (!nameA.endsWith(".part") && nameB.endsWith(".part")) return -1;
            return a.compareTo(b);
        });

        return matchingFiles.get(0).toAbsolutePath().toString();
    }
}
