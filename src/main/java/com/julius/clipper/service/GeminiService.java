package com.julius.clipper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.julius.clipper.telemetry.AiMetrics;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final String apiKey;
    private final String modelName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AiMetrics aiMetrics;

    public GeminiService(
            com.julius.clipper.config.properties.AiProperties aiProperties,
            AiMetrics aiMetrics) {
        this.apiKey = aiProperties.geminiApiKey();
        this.modelName = aiProperties.geminiModel();
        this.aiMetrics = aiMetrics;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> analyzeFullTranscript(
            String fullText, 
            List<Map<String, Object>> chunks, 
            double minDuration, 
            double maxDuration, 
            int topN,
            List<Map<String, Object>> acceptedWindows) throws Exception {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Google Gemini API key is missing. Please set the 'google.api.key' environment variable or configuration property.");
        }

        String prompt = buildPrompt(fullText, chunks, minDuration, maxDuration, topN, acceptedWindows);

        // Build exact JSON Schema corresponding to Python's response_json_schema
        Map<String, Object> clipProperties = new LinkedHashMap<>();
        
        Map<String, Object> numberType = new LinkedHashMap<>();
        numberType.put("type", "NUMBER");
        
        Map<String, Object> integerType = new LinkedHashMap<>();
        integerType.put("type", "INTEGER");
        
        Map<String, Object> stringType = new LinkedHashMap<>();
        stringType.put("type", "STRING");

        clipProperties.put("start", numberType);
        clipProperties.put("end", numberType);
        clipProperties.put("score", integerType);
        clipProperties.put("reasoning", stringType);
        clipProperties.put("pov_en", stringType);
        clipProperties.put("pov_hi", stringType);
        clipProperties.put("text", stringType);

        Map<String, Object> clipSchema = new LinkedHashMap<>();
        clipSchema.put("type", "OBJECT");
        clipSchema.put("properties", clipProperties);
        clipSchema.put("required", Arrays.asList("start", "end", "score", "reasoning", "pov_en", "pov_hi", "text"));

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "ARRAY");
        rootSchema.put("items", clipSchema);

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", rootSchema);

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> contentNode = new LinkedHashMap<>();
        contentNode.put("parts", Collections.singletonList(textPart));

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("contents", Collections.singletonList(contentNode));
        requestPayload.put("generationConfig", generationConfig);

        String jsonRequestBody = objectMapper.writeValueAsString(requestPayload);
        String requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofMinutes(3))
                .build();

        log.info("Sending content generation request to Gemini model: {}", modelName);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            String errResponse = response.body();
            log.error("Gemini API call failed with status {}. Error details: {}", response.statusCode(), errResponse);
            throw new IOException("Gemini API call failed with status " + response.statusCode() + ": " + errResponse);
        }

        Map<String, Object> rootResponse = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        
        if (aiMetrics != null) {
            Map<String, Object> usageMetadata = (Map<String, Object>) rootResponse.get("usageMetadata");
            if (usageMetadata != null) {
                Number promptTokens = (Number) usageMetadata.get("promptTokenCount");
                Number completionTokens = (Number) usageMetadata.get("candidatesTokenCount");
                if (promptTokens != null) {
                    aiMetrics.recordGeminiTokens(modelName, "prompt", promptTokens.longValue());
                }
                if (completionTokens != null) {
                    aiMetrics.recordGeminiTokens(modelName, "completion", completionTokens.longValue());
                }
            }
        }

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) rootResponse.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new NoSuchElementException("Gemini API returned an empty list of generation candidates.");
        }

        Map<String, Object> candidate = candidates.get(0);
        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
        if (content == null) {
            throw new NoSuchElementException("Gemini API candidate content structure is missing.");
        }

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new NoSuchElementException("Gemini API candidate content has no parts.");
        }

        String rawJsonOutput = (String) parts.get(0).get("text");
        if (rawJsonOutput == null || rawJsonOutput.isBlank()) {
            throw new IllegalStateException("Gemini API returned an empty text block.");
        }

        // Clean up potential markdown formatting code blocks returned by LLM
        String cleanJson = rawJsonOutput.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        log.info("Received structured clip schema JSON response from Gemini API.");
        return objectMapper.readValue(cleanJson, new TypeReference<List<Map<String, Object>>>() {});
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> transcribeAudio(File wavFile) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Google Gemini API key is missing. Please set the 'google.api.key' config parameter.");
        }

        log.info("Initiating native audio upload for transcription: {}", wavFile.getAbsolutePath());
        String fileUri = uploadFile(wavFile, "audio/wav");
        log.info("Successfully uploaded audio to Gemini Files space. URI: {}", fileUri);

        String prompt = "Transcribe the provided audio recording. Produce a structured JSON list of segments. " +
                "For every segment, specify the start and end timestamps in seconds (decimal format) and the literal text. " +
                "Do not summarize or skip anything. Ensure transcription is highly detailed and word-accurate.";

        // Build exact structured JSON schema for transcript segments
        Map<String, Object> properties = new LinkedHashMap<>();
        
        Map<String, Object> numberType = new LinkedHashMap<>();
        numberType.put("type", "NUMBER");
        
        Map<String, Object> stringType = new LinkedHashMap<>();
        stringType.put("type", "STRING");

        properties.put("start", numberType);
        properties.put("end", numberType);
        properties.put("text", stringType);

        Map<String, Object> segmentSchema = new LinkedHashMap<>();
        segmentSchema.put("type", "OBJECT");
        segmentSchema.put("properties", properties);
        segmentSchema.put("required", Arrays.asList("start", "end", "text"));

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "ARRAY");
        rootSchema.put("items", segmentSchema);

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", rootSchema);

        Map<String, Object> fileNode = new LinkedHashMap<>();
        fileNode.put("mimeType", "audio/wav");
        fileNode.put("fileUri", fileUri);

        Map<String, Object> filePart = new LinkedHashMap<>();
        filePart.put("fileData", fileNode);

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> contentNode = new LinkedHashMap<>();
        contentNode.put("parts", Arrays.asList(filePart, textPart));

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("contents", Collections.singletonList(contentNode));
        requestPayload.put("generationConfig", generationConfig);

        String jsonRequestBody = objectMapper.writeValueAsString(requestPayload);
        String requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofMinutes(5))
                .build();

        log.info("Sending generateContent transcription request for audio file to Gemini 1.5 Flash.");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Clean up file from Gemini Files Storage immediately
        try {
            String fileId = fileUri.substring(fileUri.lastIndexOf('/') + 1);
            String deleteUrl = "https://generativelanguage.googleapis.com/v1beta/files/" + fileId + "?key=" + apiKey;
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(deleteUrl))
                    .DELETE()
                    .build();
            httpClient.send(deleteRequest, HttpResponse.BodyHandlers.discarding());
            log.info("Cleaned up file from Gemini Files storage: {}", fileUri);
        } catch (Exception e) {
            log.warn("Failed to delete file from Gemini storage workspace: {}", e.getMessage());
        }

        if (response.statusCode() != 200) {
            String errResponse = response.body();
            log.error("Gemini audio transcription generateContent failed with status {}. Details: {}", response.statusCode(), errResponse);
            throw new IOException("Gemini audio transcription failed: " + errResponse);
        }

        Map<String, Object> rootResponse = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        
        if (aiMetrics != null) {
            Map<String, Object> usageMetadata = (Map<String, Object>) rootResponse.get("usageMetadata");
            if (usageMetadata != null) {
                Number promptTokens = (Number) usageMetadata.get("promptTokenCount");
                Number completionTokens = (Number) usageMetadata.get("candidatesTokenCount");
                if (promptTokens != null) {
                    aiMetrics.recordGeminiTokens("gemini-1.5-flash", "prompt", promptTokens.longValue());
                }
                if (completionTokens != null) {
                    aiMetrics.recordGeminiTokens("gemini-1.5-flash", "completion", completionTokens.longValue());
                }
            }
        }

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) rootResponse.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new NoSuchElementException("Gemini returned empty transcription candidates.");
        }

        Map<String, Object> candidate = candidates.get(0);
        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        String rawJson = (String) parts.get(0).get("text");

        String cleanJson = rawJson.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        log.info("Received transcription segments JSON array from Gemini API.");
        return objectMapper.readValue(cleanJson, new TypeReference<List<Map<String, Object>>>() {});
    }

    @SuppressWarnings("unchecked")
    private String uploadFile(File file, String mimeType) throws Exception {
        // Step 1: Initialize resumable file upload protocol session
        String sessionUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + apiKey;
        
        Map<String, Object> fileMetadata = new LinkedHashMap<>();
        fileMetadata.put("displayName", file.getName());
        
        Map<String, Object> initRequestPayload = new LinkedHashMap<>();
        initRequestPayload.put("file", fileMetadata);

        String jsonMetadata = objectMapper.writeValueAsString(initRequestPayload);

        HttpRequest initRequest = HttpRequest.newBuilder()
                .uri(URI.create(sessionUrl))
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", String.valueOf(file.length()))
                .header("X-Goog-Upload-Header-Content-Type", mimeType)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMetadata, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> initResponse = httpClient.send(initRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (initResponse.statusCode() != 200) {
            throw new IOException("Failed to initialize resumable upload session. Code: " + initResponse.statusCode() + ", Body: " + initResponse.body());
        }

        String uploadUrl = initResponse.headers().firstValue("X-Goog-Upload-URL")
                .orElseThrow(() -> new IOException("X-Goog-Upload-URL header was missing in upload initiation response."));

        // Step 2: Upload file bytes
        HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "finalize")
                .header("Content-Length", String.valueOf(file.length()))
                .PUT(HttpRequest.BodyPublishers.ofFile(file.toPath()))
                .timeout(Duration.ofMinutes(10))
                .build();

        HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (uploadResponse.statusCode() != 200) {
            throw new IOException("Failed to upload target binary bytes to resumable endpoint. Code: " + uploadResponse.statusCode() + ", Body: " + uploadResponse.body());
        }

        Map<String, Object> uploadResult = objectMapper.readValue(uploadResponse.body(), new TypeReference<Map<String, Object>>() {});
        Map<String, Object> fileNode = (Map<String, Object>) uploadResult.get("file");
        if (fileNode == null) {
            throw new IOException("File node is missing in upload response payload: " + uploadResponse.body());
        }

        return (String) fileNode.get("uri");
    }

    private String buildPrompt(
            String fullText, 
            List<Map<String, Object>> chunks, 
            double minDuration, 
            double maxDuration, 
            int topN,
            List<Map<String, Object>> acceptedWindows) throws Exception {

        String chunksJson = objectMapper.writeValueAsString(chunks);
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a viral content expert. Analyze the full video transcript broken into chunks with timestamps.\n");
        prompt.append("Reconstruct the meaning, resolve speech-to-text noise, and identify the top ").append(topN).append(" most viral standalone segments.\n\n");
        prompt.append("Video Transcript:\n").append(fullText).append("\n\n");
        
        prompt.append("MANDATORY CONSTRAINTS:\n");
        prompt.append("- Return exactly ").append(topN).append(" segments.\n");
        prompt.append("- Segment duration must be within [").append(minDuration).append("s, ").append(maxDuration).append("s].\n");
        prompt.append("- Each segment must be standalone, having a clear hook, plot development, and natural punchline/conclusion.\n");
        prompt.append("- Do not cut segments mid-thought, mid-sentence, or mid-phrase.\n");
        prompt.append("- pov_en must be 5 to 6 words (max 7). pov_hi must be natural conversational Hindi in Devanagari script (5 to 6 words).\n\n");

        if (acceptedWindows != null && !acceptedWindows.isEmpty()) {
            prompt.append("EXCLUSION RULE: A previous pass already selected segments. Do NOT return duplicate segments that overlap with these ranges:\n");
            prompt.append(objectMapper.writeValueAsString(acceptedWindows)).append("\n\n");
        }

        prompt.append("Transcript Chunks JSON:\n").append(chunksJson).append("\n\n");
        prompt.append("Output your response strictly as a JSON array matching the required JSON schema.");

        return prompt.toString();
    }
}
