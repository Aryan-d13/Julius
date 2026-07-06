package com.julius.clipper.pipeline.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.Task;
import com.julius.clipper.pipeline.Worker;
import com.julius.clipper.service.SegmentMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component("TRANSCRIBEWorker")
public class TranscribeWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(TranscribeWorker.class);

    private final SegmentMerger segmentMerger;
    private final String cacheDir;
    private final String pythonPath;
    private final String pythonPathEnv;
    private final String whisperModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TranscribeWorker(SegmentMerger segmentMerger,
                            @Value("${clipper.cache.dir:data/library/cache}") String cacheDir,
                            @Value("${clipper.python.path}") String pythonPath,
                            @Value("${clipper.python.env}") String pythonPathEnv,
                            @Value("${clipper.whisper.model:large-v3-turbo}") String whisperModel) {
        this.segmentMerger = segmentMerger;
        this.cacheDir = cacheDir;
        this.pythonPath = pythonPath;
        this.pythonPathEnv = pythonPathEnv;
        this.whisperModel = whisperModel;
        new File(cacheDir).mkdirs();
    }

    @Override
    public Map<String, Object> process(Task task) throws Exception {
        String clipId = (String) task.getPayload().get("clip_id");
        String audioKey = (String) task.getPayload().get("storage_key");
        String userId = task.getUserId();

        log.info("TranscribeWorker starting transcription workflow for clipId: {}, audioKey: {}", clipId, audioKey);

        String cacheFilename = (userId != null ? userId + "_" : "") + clipId + "_transcript.json";
        Path cacheFilePath = Paths.get(cacheDir, cacheFilename);

        // 1. Evaluate cache hit
        if (Files.exists(cacheFilePath)) {
            try {
                String cachedContent = Files.readString(cacheFilePath, StandardCharsets.UTF_8);
                if (cachedContent.length() > 10) {
                    Map<String, Object> cachedData = objectMapper.readValue(cachedContent, new TypeReference<Map<String, Object>>() {});
                    if (cachedData.containsKey("segments") || cachedData.containsKey("text")) {
                        log.info("Transcript loaded from library cache: {}", cacheFilePath);
                        return Map.of(
                                "transcript_key", cacheFilePath.toAbsolutePath().toString(),
                                "clip_id", clipId
                        );
                    }
                }
            } catch (Exception e) {
                log.warn("Corrupt or invalid transcript cache found at {}, re-transcribing: {}", cacheFilePath, e.getMessage());
            }
        }

        File audioFile = new File(audioKey);
        if (!audioFile.exists()) {
            throw new FileNotFoundException("Input audio file not found on disk: " + audioKey);
        }

        // Create a temporary JSON output file path for the python bridge
        File tempJsonFile = File.createTempFile("whisper_raw_", ".json");
        tempJsonFile.deleteOnExit();

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
                "--audio", audioFile.getAbsolutePath(),
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
        Process process = pb.start();

        // Log python output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[Python Whisper] {}", line);
            }
        }

        int exitCode = process.waitFor();
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

        // 6. Persist transcript to Cache
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(transcriptionResult);
        Files.writeString(cacheFilePath, prettyJson, StandardCharsets.UTF_8);

        log.info("Audio transcription successfully completed and cached at: {}", cacheFilePath);
        return Map.of(
                "transcript_key", cacheFilePath.toAbsolutePath().toString(),
                "clip_id", clipId
        );
    }
}
