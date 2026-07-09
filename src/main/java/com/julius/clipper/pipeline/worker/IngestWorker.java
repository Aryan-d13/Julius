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
    private final com.julius.clipper.service.StorageClient storageClient;

    public IngestWorker(MediaConverter converter,
                        com.julius.clipper.service.StorageClient storageClient) {
        this.converter = converter;
        this.storageClient = storageClient;
    }

    @Override
    public Map<String, Object> process(Task task) throws Exception {
        String filePath = (String) task.getPayload().get("file_path");
        String userId = task.getUserId();

        log.info("IngestWorker processing key: {}", filePath);

        File tempInputFile = File.createTempFile("ingest_raw_", ".mp4");
        tempInputFile.deleteOnExit();

        try {
            // Download user upload locally for processing
            try (OutputStream out = new FileOutputStream(tempInputFile)) {
                storageClient.download(filePath, out);
            }

            // Generate SHA-256 hash for deduplication
            String sha256Hash = calculateSHA256(tempInputFile);
            String clipId = "file_" + sha256Hash.substring(0, 16);
            log.info("Generated assets deduplication ID: {}", clipId);

            String originalExtension = getFileExtension(filePath, ".mp4");
            String targetVideoKey = com.julius.clipper.service.StorageKeyBuilder.libraryVideo(clipId, originalExtension, userId);

            if (!storageClient.exists(targetVideoKey)) {
                log.info("Uploading source file to library: {}", targetVideoKey);
                try (InputStream in = new FileInputStream(tempInputFile)) {
                    storageClient.upload(new com.julius.clipper.service.UploadRequest(
                        targetVideoKey,
                        in,
                        tempInputFile.length(),
                        "video/mp4",
                        null,
                        null
                    ));
                }
            } else {
                log.info("Source file already matched by hash in library: {}", targetVideoKey);
            }

            // Extract WAV audio via converter
            String temporaryWavPath = converter.convertToWav(tempInputFile.getAbsolutePath());
            File tempWavFile = new File(temporaryWavPath);
            String targetAudioKey = com.julius.clipper.service.StorageKeyBuilder.libraryAudio(clipId, userId);

            try {
                if (!storageClient.exists(targetAudioKey)) {
                    log.info("Uploading processed audio to library: {}", targetAudioKey);
                    try (InputStream in = new FileInputStream(tempWavFile)) {
                        storageClient.upload(new com.julius.clipper.service.UploadRequest(
                            targetAudioKey,
                            in,
                            tempWavFile.length(),
                            "audio/wav",
                            null,
                            null
                        ));
                    }
                } else {
                    log.info("Processed audio track already matched by hash in library: {}", targetAudioKey);
                }
            } finally {
                if (tempWavFile.exists()) {
                    tempWavFile.delete();
                }
            }

            Map<String, Object> outputPayload = new HashMap<>();
            outputPayload.put("video_key", targetVideoKey);
            outputPayload.put("storage_key", targetAudioKey);
            outputPayload.put("clip_id", clipId);

            return outputPayload;

        } finally {
            if (tempInputFile.exists()) {
                tempInputFile.delete();
            }
        }
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
