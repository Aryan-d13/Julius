package com.julius.clipper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EditorEngine {
    private static final Logger log = LoggerFactory.getLogger(EditorEngine.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EditSessionRepository sessionRepository;
    private final ClipVersionRepository versionRepository;
    private final SubtitleStyleRepository styleRepository;
    private final JobClipRepository jobClipRepository;

    public EditorEngine(
            EditSessionRepository sessionRepository,
            ClipVersionRepository versionRepository,
            SubtitleStyleRepository styleRepository,
            JobClipRepository jobClipRepository) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.styleRepository = styleRepository;
        this.jobClipRepository = jobClipRepository;
    }

    @Transactional
    public EditSession createSession(String clipId, String name) throws Exception {
        log.info("Creating new edit session for clip: {}", clipId);

        JobClip clip = jobClipRepository.findById(clipId)
                .orElseThrow(() -> new NoSuchElementException("JobClip not found: " + clipId));

        EditSession session = EditSession.builder()
                .clipId(clipId)
                .name(name)
                .build();
        session = sessionRepository.save(session);

        // Seed a default style preset
        SubtitleStyle style = SubtitleStyle.builder()
                .name("TikTok Pop")
                .fontName("Impact")
                .fontSize(80)
                .primaryColor("#FFFF00") // Yellow
                .secondaryColor("#FF0000") // Red
                .outlineColor("#000000") // Black outline
                .shadowColor("#000000")
                .outlineWidth(6.0)
                .shadowDepth(0.0)
                .alignment(2) // ASS bottom center
                .safeZoneVertical(250)
                .build();
        style = styleRepository.save(style);

        // Build seed timeline state from immutable parent clip details
        String timelineState = buildSeedTimelineState(clip);

        ClipVersion firstVersion = ClipVersion.builder()
                .session(session)
                .versionNumber(1)
                .name("Initial AI Output")
                .timelineStateJson(timelineState)
                .stylePreset(style)
                .build();
        versionRepository.save(firstVersion);

        logEditorEvent(session.getId(), "ClipCreated", "{\"clipId\":\"" + clipId + "\"}");

        return session;
    }

    @Transactional
    public ClipVersion saveAutosave(String sessionId, String timelineStateJson, String stylePresetId) throws Exception {
        log.debug("Processing autosave for session: {}", sessionId);

        EditSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("EditSession not found: " + sessionId));

        SubtitleStyle style = styleRepository.findById(stylePresetId)
                .orElseThrow(() -> new NoSuchElementException("SubtitleStyle not found: " + stylePresetId));

        Optional<ClipVersion> latestOpt = versionRepository.findLatestVersionForSession(sessionId);
        int nextVersionNumber = latestOpt.map(v -> v.getVersionNumber() + 1).orElse(1);

        ClipVersion newVersion = ClipVersion.builder()
                .session(session)
                .versionNumber(nextVersionNumber)
                .name("Autosave #" + nextVersionNumber)
                .timelineStateJson(timelineStateJson)
                .stylePreset(style)
                .build();
        newVersion = versionRepository.save(newVersion);

        // Update session timestamp
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        logEditorEvent(sessionId, "TranscriptEdited", "{\"versionNumber\":" + nextVersionNumber + "}");
        return newVersion;
    }

    @Transactional
    public ClipVersion createNamedCheckpoint(String sessionId, String checkpointName, String timelineStateJson, String stylePresetId) throws Exception {
        log.info("Creating manual checkpoint version '{}' for session: {}", checkpointName, sessionId);

        EditSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("EditSession not found: " + sessionId));

        SubtitleStyle style = styleRepository.findById(stylePresetId)
                .orElseThrow(() -> new NoSuchElementException("SubtitleStyle not found: " + stylePresetId));

        Optional<ClipVersion> latestOpt = versionRepository.findLatestVersionForSession(sessionId);
        int nextVersionNumber = latestOpt.map(v -> v.getVersionNumber() + 1).orElse(1);

        ClipVersion newVersion = ClipVersion.builder()
                .session(session)
                .versionNumber(nextVersionNumber)
                .name(checkpointName)
                .timelineStateJson(timelineStateJson)
                .stylePreset(style)
                .build();
        newVersion = versionRepository.save(newVersion);

        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        logEditorEvent(sessionId, "SubtitleStyleChanged", "{\"checkpointName\":\"" + checkpointName + "\"}");
        return newVersion;
    }

    private void logEditorEvent(String sessionId, String eventType, String jsonPayload) {
        log.info("EDITOR_EVENT: sessionId={}, eventType={}, payload={}, timestamp={}",
                sessionId, eventType, jsonPayload, LocalDateTime.now());
    }

    private String buildSeedTimelineState(JobClip clip) throws Exception {
        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("durationSeconds", clip.getDurationSeconds());

        Map<String, Object> track = new LinkedHashMap<>();
        track.put("id", "track-sub");
        track.put("type", "SUBTITLE");
        track.put("name", "Dialogue Subtitles");
        track.put("isMuted", false);

        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("id", "seg-1");
        segment.put("assetId", clip.getId()); // Referencing parent JobClip asset id!
        segment.put("sourceStart", 0.0);
        segment.put("timelineStart", 0.0);
        segment.put("duration", clip.getDurationSeconds());

        // Simple default seed word lists
        List<Map<String, Object>> words = new ArrayList<>();
        words.add(Map.of("text", "Welcome", "start", 0.0, "end", 1.0));
        words.add(Map.of("text", "to", "start", 1.1, "end", 1.7));
        words.add(Map.of("text", "the", "start", 1.8, "end", 2.4));
        words.add(Map.of("text", "future", "start", 2.5, "end", 3.5));
        
        segment.put("words", words);
        track.put("segments", Collections.singletonList(segment));
        timeline.put("tracks", Collections.singletonList(track));

        return objectMapper.writeValueAsString(timeline);
    }
}
