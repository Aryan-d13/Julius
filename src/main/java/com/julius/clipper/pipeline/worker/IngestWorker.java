package com.julius.clipper.pipeline.worker;

import com.julius.clipper.domain.Task;
import com.julius.clipper.pipeline.Worker;
import com.julius.clipper.service.MediaConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Component("INGESTWorker")
public class IngestWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(IngestWorker.class);

    private final MediaConverter converter;
    private final String libraryVideoDir;
    private final String libraryAudioDir;

    public IngestWorker(MediaConverter converter,
                        @Value("${clipper.library.video.dir:data/library/videos}") String libraryVideoDir,
                        @Value("${clipper.library.audio.dir:data/library/audios}") String libraryAudioDir) {
        this.converter = converter;
        this.libraryVideoDir = libraryVideoDir;
        this.libraryAudioDir = libraryAudioDir;

        new File(libraryVideoDir).mkdirs();
        new File(libraryAudioDir).mkdirs();
    }

    @Override
    public Map<String, Object> process(Task task) throws Exception {
        String filePath = (String) task.getPayload().get("file_path");
        String userId = task.getUserId();

        log.info("IngestWorker processing file: {}", filePath);

        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("Local file for ingestion not found: " + filePath);
        }

        // Generate SHA-256 hash for deduplication
        String sha256Hash = calculateSHA256(inputFile);
        String clipId = "file_" + sha256Hash.substring(0, 16);
        log.info("Generated assets deduplication ID: {}", clipId);

        String originalExtension = getFileExtension(inputFile.getName(), ".mp4");
        String outputVideoName = (userId != null ? userId + "_" : "") + clipId + originalExtension;
        Path targetVideoPath = Paths.get(libraryVideoDir, outputVideoName);

        if (!Files.exists(targetVideoPath)) {
            log.info("Copying source file to library: {}", targetVideoPath);
            Files.copy(inputFile.toPath(), targetVideoPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            log.info("Source file already matched by hash in library: {}", targetVideoPath);
        }

        // Extract WAV audio via converter
        String temporaryWavPath = converter.convertToWav(targetVideoPath.toString());
        String outputAudioName = (userId != null ? userId + "_" : "") + clipId + ".wav";
        Path targetAudioPath = Paths.get(libraryAudioDir, outputAudioName);

        if (!Files.exists(targetAudioPath)) {
            log.info("Moving processed audio to library: {}", targetAudioPath);
            Files.move(Paths.get(temporaryWavPath), targetAudioPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            log.info("Processed audio track already matched by hash in library: {}", targetAudioPath);
            File tempWavFile = new File(temporaryWavPath);
            if (tempWavFile.exists()) {
                tempWavFile.delete();
            }
        }

        Map<String, Object> outputPayload = new HashMap<>();
        outputPayload.put("video_key", targetVideoPath.toAbsolutePath().toString());
        outputPayload.put("storage_key", targetAudioPath.toAbsolutePath().toString());
        outputPayload.put("clip_id", clipId);

        return outputPayload;
    }

    private String calculateSHA256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int readBytes;
            while ((readBytes = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, readBytes);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String getFileExtension(String filename, String defaultExtension) {
        int index = filename.lastIndexOf('.');
        if (index > 0) {
            return filename.substring(index);
        }
        return defaultExtension;
    }
}
