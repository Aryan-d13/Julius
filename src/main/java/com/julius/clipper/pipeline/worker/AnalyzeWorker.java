package com.julius.clipper.pipeline.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.Task;
import com.julius.clipper.pipeline.Worker;
import com.julius.clipper.service.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component("ANALYZEWorker")
public class AnalyzeWorker implements Worker {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeWorker.class);

    private final GeminiService geminiService;
    private final String cacheDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyzeWorker(GeminiService geminiService,
                         @Value("${clipper.cache.dir:data/library/cache}") String cacheDir) {
        this.geminiService = geminiService;
        this.cacheDir = cacheDir;
        new File(cacheDir).mkdirs();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> process(Task task) throws Exception {
        String clipId = (String) task.getPayload().get("clip_id");
        String transcriptKey = (String) task.getPayload().get("transcript_key");
        String userId = task.getUserId();

        int topN = getIntValue(task.getPayload(), "top_n", 2);
        double minDuration = getDoubleValue(task.getPayload(), "min_duration", 30.0);
        double maxDuration = getDoubleValue(task.getPayload(), "max_duration", 900.0);

        log.info("AnalyzeWorker starting: clipId {}, topN {}, minDuration={}, maxDuration={}", clipId, topN, minDuration, maxDuration);

        File transcriptFile = new File(transcriptKey);
        if (!transcriptFile.exists()) {
            throw new FileNotFoundException("Transcript cache file not found: " + transcriptKey);
        }

        String rawTranscriptJson = Files.readString(transcriptFile.toPath(), StandardCharsets.UTF_8);
        Map<String, Object> transcriptMap = objectMapper.readValue(rawTranscriptJson, new TypeReference<Map<String, Object>>() {});
        
        String fullText = (String) transcriptMap.get("text");
        List<Map<String, Object>> segments = (List<Map<String, Object>>) transcriptMap.get("segments");

        // First pass call to Gemini REST API
        List<Map<String, Object>> LLMClips = geminiService.analyzeFullTranscript(
                fullText, segments, minDuration, maxDuration, topN, Collections.emptyList()
        );

        Set<String> processedWindows = new HashSet<>();
        List<Map<String, Object>> verifiedClips = extractAndVerifyClips(LLMClips, minDuration, maxDuration, processedWindows);

        // Schema validation retry if Gemini returns fewer clips than topN
        if (verifiedClips.size() < topN) {
            int missingCount = topN - verifiedClips.size();
            log.warn("Gemini returned {} verified clips out of {}. Executing retry pass for {} remaining slots...", verifiedClips.size(), topN, missingCount);

            List<Map<String, Object>> exclusions = new ArrayList<>();
            for (Map<String, Object> c : verifiedClips) {
                exclusions.add(Map.of(
                        "start", c.get("start"),
                        "end", c.get("end")
                ));
            }

            try {
                List<Map<String, Object>> retryLLMClips = geminiService.analyzeFullTranscript(
                        fullText, segments, minDuration, maxDuration, missingCount, exclusions
                );
                List<Map<String, Object>> verifiedRetryClips = extractAndVerifyClips(retryLLMClips, minDuration, maxDuration, processedWindows);
                verifiedClips.addAll(verifiedRetryClips);
            } catch (Exception e) {
                log.error("Gemini retry pass failed: {}. Continuing with partial clips list.", e.getMessage());
            }
        }

        List<Map<String, Object>> finalClipsList = verifiedClips.size() > topN ? verifiedClips.subList(0, topN) : verifiedClips;

        // Save analysis findings to cache
        String cacheFilename = (userId != null ? userId + "_" : "") + clipId + "_analysis.json";
        Path cacheFilePath = Paths.get(cacheDir, cacheFilename);

        Map<String, Object> cachedAnalysisPayload = new HashMap<>();
        cachedAnalysisPayload.put("best_clips", finalClipsList);
        cachedAnalysisPayload.put("requested_clips", topN);
        cachedAnalysisPayload.put("returned_clips", finalClipsList.size());
        cachedAnalysisPayload.put("partial_results", finalClipsList.size() < topN);

        String prettyJsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cachedAnalysisPayload);
        Files.writeString(cacheFilePath, prettyJsonBytes, StandardCharsets.UTF_8);

        log.info("Analysis successfully completed and saved: {}", cacheFilePath);

        Map<String, Object> outputMap = new HashMap<>();
        outputMap.put("best_clips", finalClipsList);
        outputMap.put("clip_id", clipId);
        outputMap.put("analysis_results_cache", cacheFilePath.toAbsolutePath().toString());

        return outputMap;
    }

    private List<Map<String, Object>> extractAndVerifyClips(List<Map<String, Object>> clips, double minDuration, double maxDuration, Set<String> processedWindows) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (clips == null) return result;

        for (Map<String, Object> clip : clips) {
            try {
                verifyClipSchema(clip);
                double start = ((Number) clip.get("start")).doubleValue();
                double end = ((Number) clip.get("end")).doubleValue();
                double duration = end - start;

                if (duration < minDuration || duration > maxDuration) {
                    log.warn("Clip bounds failed duration criteria ({}s): [{}s, {}s]", duration, minDuration, maxDuration);
                    continue;
                }

                String windowKey = String.format(Locale.US, "%.2f_%.2f", start, end);
                if (processedWindows.contains(windowKey)) {
                    log.warn("Clip window matches duplicate selection: {}", windowKey);
                    continue;
                }

                processedWindows.add(windowKey);
                result.add(clip);
            } catch (Exception e) {
                log.warn("Clip rejected during schema validation check: {} -> {}", clip, e.getMessage());
            }
        }
        return result;
    }

    private void verifyClipSchema(Map<String, Object> clip) {
        if (!clip.containsKey("start") || !clip.containsKey("end") || !clip.containsKey("score")
                || !clip.containsKey("reasoning") || !clip.containsKey("pov_en") || !clip.containsKey("pov_hi")
                || !clip.containsKey("text")) {
            throw new IllegalArgumentException("One or more required fields in the clip structure schema are missing.");
        }
        if (!(clip.get("start") instanceof Number) || !(clip.get("end") instanceof Number) || !(clip.get("score") instanceof Number)) {
            throw new IllegalArgumentException("Field types for start, end, or score in the clip schema are invalid.");
        }
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return defaultVal;
    }

    private double getDoubleValue(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return defaultVal;
    }
}
