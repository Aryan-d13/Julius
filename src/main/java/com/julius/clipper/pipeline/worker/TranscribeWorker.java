package com.julius.clipper.pipeline.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.Task;
import com.julius.clipper.pipeline.Worker;
import com.julius.clipper.service.SegmentMerger;
import com.julius.clipper.telemetry.AiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component("TRANSCRIBEWorker")
public class TranscribeWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(TranscribeWorker.class);

    private final SegmentMerger segmentMerger;
    private final String pythonPath;
    private final String pythonPathEnv;
    private final String whisperModel;
    private final com.julius.clipper.service.StorageClient storageClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AiMetrics aiMetrics;

    public TranscribeWorker(SegmentMerger segmentMerger,
                            com.julius.clipper.config.properties.AiProperties aiProperties,
                            com.julius.clipper.service.StorageClient storageClient,
                            AiMetrics aiMetrics) {
        this.segmentMerger = segmentMerger;
        this.pythonPath = aiProperties.whisper().pythonPath();
        this.pythonPathEnv = aiProperties.whisper().pythonEnv();
        this.whisperModel = aiProperties.whisper().model();
        this.storageClient = storageClient;
        this.aiMetrics = aiMetrics;
    }

    @Override
    public Map<String, Object> process(Task task) throws Exception {
        String clipId = (String) task.getPayload().get("clip_id");
        String audioKey = (String) task.getPayload().get("storage_key");
        String jobId = task.getJobId();

        log.info("TranscribeWorker starting transcription workflow for clipId: {}, audioKey: {}, jobId: {}", clipId, audioKey, jobId);

        String cacheKey = com.julius.clipper.service.StorageKeyBuilder.jobTranscript(jobId, clipId);

        // 1. Evaluate cache hit
        if (storageClient.exists(cacheKey)) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                storageClient.download(cacheKey, baos);
                String cachedContent = baos.toString(StandardCharsets.UTF_8);
                if (cachedContent.length() > 10) {
                    Map<String, Object> cachedData = objectMapper.readValue(cachedContent, new TypeReference<Map<String, Object>>() {});
                    if (cachedData.containsKey("segments") || cachedData.containsKey("text")) {
                        log.info("Transcript loaded from cloud storage cache: {}", cacheKey);
                        return Map.of(
                                "transcript_key", cacheKey,
                                "clip_id", clipId
                        );
                    }
                }
            } catch (Exception e) {
                log.warn("Corrupt or invalid transcript cache found at key {}, re-transcribing: {}", cacheKey, e.getMessage());
            }
        }

        File tempAudioFile = File.createTempFile("transcribe_track_", ".wav");
        tempAudioFile.deleteOnExit();

        File tempJsonFile = File.createTempFile("whisper_raw_", ".json");
        tempJsonFile.deleteOnExit();

        try {
            // Download the source audio track from cloud storage locally for Whisper to process
            try (OutputStream out = new FileOutputStream(tempAudioFile)) {
                storageClient.download(audioKey, out);
            }

            // Resolve absolute python path dynamically if relative is provided
            File pythonExecFile = new File(pythonPath);
            if (!pythonExecFile.isAbsolute()) {
                pythonExecFile = new File(System.getProperty("user.dir"), pythonPath);
            }

            // 2. Invoke local python script using ProcessBuilder
            String scriptPath = "scripts/transcribe_bridge.py";
            List<String> command = List.of(
                    pythonExecFile.getAbsolutePath(),
                    scriptPath,
                    "--audio", tempAudioFile.getAbsolutePath(),
                    "--output", tempJsonFile.getAbsolutePath(),
                    "--model", whisperModel
            );

            log.info("Executing Python Whisper bridge subprocess: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            
            // Inherit directory context from config or fallback to project root
            if (pythonPathEnv != null && !pythonPathEnv.isBlank()) {
                File envDir = new File(pythonPathEnv);
                if (!envDir.isAbsolute()) {
                    envDir = new File(System.getProperty("user.dir"), pythonPathEnv);
                }
                if (envDir.exists() && envDir.isDirectory()) {
                    pb.directory(envDir);
                }
            } else {
                pb.directory(new File(System.getProperty("user.dir")));
            }
            
            pb.redirectErrorStream(true);
            
            io.micrometer.core.instrument.Timer.Sample whisperSample = aiMetrics != null ? aiMetrics.startTranscription() : null;
            String whisperStatus = "success";
            int exitCode = -1;

            try {
                Process process = pb.start();

                // Log python output
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[Python Whisper] {}", line);
                    }
                }

                exitCode = process.waitFor();
                if (exitCode != 0) {
                    whisperStatus = "failed";
                }
            } catch (Exception e) {
                whisperStatus = "failed";
                throw e;
            } finally {
                if (whisperSample != null) {
                    aiMetrics.recordTranscription(whisperSample, whisperModel, whisperStatus);
                }
            }

            if (exitCode != 0) {
                throw new RuntimeException("Python transcription bridge script failed with exit code: " + exitCode);
            }

            // 3. Read raw segments list from output JSON
            if (!tempJsonFile.exists() || tempJsonFile.length() == 0) {
                throw new FileNotFoundException("Python bridge failed to output transcription JSON at expected path: " + tempJsonFile.getAbsolutePath());
            }

            List<Map<String, Object>> rawSegments = objectMapper.readValue(tempJsonFile, new TypeReference<List<Map<String, Object>>>() {});

            // 4. Post-process segments using SegmentMerger
            log.info("Merging transcription chunks into line-level timestamps.");
            List<Map<String, Object>> mergedSegments = segmentMerger.mergeMaps(rawSegments, false);

            // 5. Construct transcription output payload
            StringBuilder fullTranscriptBuilder = new StringBuilder();
            for (Map<String, Object> segment : mergedSegments) {
                String text = (String) segment.get("text");
                if (text != null) {
                    fullTranscriptBuilder.append(text.trim()).append(" ");
                }
            }

            Map<String, Object> transcriptionResult = new LinkedHashMap<>();
            transcriptionResult.put("text", fullTranscriptBuilder.toString().trim());
            transcriptionResult.put("segments", mergedSegments);

            // 6. Persist transcript to Storage Cache
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(transcriptionResult);
            byte[] jsonBytes = prettyJson.getBytes(StandardCharsets.UTF_8);
            try (InputStream in = new ByteArrayInputStream(jsonBytes)) {
                storageClient.upload(new com.julius.clipper.service.UploadRequest(
                    cacheKey,
                    in,
                    jsonBytes.length,
                    "application/json",
                    null,
                    null
                ));
            }

            log.info("Audio transcription successfully completed and uploaded to storage cache: {}", cacheKey);
            return Map.of(
                    "transcript_key", cacheKey,
                    "clip_id", clipId
            );

        } finally {
            if (tempAudioFile.exists()) {
                tempAudioFile.delete();
            }
            if (tempJsonFile.exists()) {
                tempJsonFile.delete();
            }
        }
    }
}
