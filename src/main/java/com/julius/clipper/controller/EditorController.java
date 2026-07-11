package com.julius.clipper.controller;

import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import com.julius.clipper.service.EditorEngine;
import com.julius.clipper.service.RenderEngine;
import com.julius.clipper.service.TimelineEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/editor")
public class EditorController {
    private static final Logger log = LoggerFactory.getLogger(EditorController.class);

    private final EditorEngine editorEngine;
    private final RenderEngine renderEngine;
    private final TimelineEngine timelineEngine;
    private final EditSessionRepository sessionRepository;
    private final ClipVersionRepository versionRepository;
    private final RenderProfileRepository profileRepository;
    private final RenderArtifactRepository artifactRepository;
    private final com.julius.clipper.service.StorageClient storageClient;

    public EditorController(
            EditorEngine editorEngine,
            RenderEngine renderEngine,
            TimelineEngine timelineEngine,
            EditSessionRepository sessionRepository,
            ClipVersionRepository versionRepository,
            RenderProfileRepository profileRepository,
            RenderArtifactRepository artifactRepository,
            com.julius.clipper.service.StorageClient storageClient) {
        this.editorEngine = editorEngine;
        this.renderEngine = renderEngine;
        this.timelineEngine = timelineEngine;
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.profileRepository = profileRepository;
        this.artifactRepository = artifactRepository;
        this.storageClient = storageClient;
    }

    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(
            @RequestParam("clipId") String clipId,
            @RequestParam("name") String name) {
        try {
            EditSession session = editorEngine.createSession(clipId, name);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            log.error("Failed to create edit session: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/latest")
    public ResponseEntity<?> getLatestVersion(@PathVariable("sessionId") String sessionId) {
        try {
            ClipVersion latest = versionRepository.findLatestVersionForSession(sessionId)
                    .orElseThrow(() -> new NoSuchElementException("No version found for session: " + sessionId));
            
            // Construct light response payload
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("id", latest.getId());
            res.put("versionNumber", latest.getVersionNumber());
            res.put("name", latest.getName());
            res.put("timelineState", latest.getTimelineStateJson());
            res.put("stylePreset", latest.getStylePreset());
            res.put("createdAt", latest.getCreatedAt());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/autosave")
    public ResponseEntity<?> saveAutosave(
            @PathVariable("sessionId") String sessionId,
            @RequestBody Map<String, String> payload) {
        try {
            String timelineJson = payload.get("timelineState");
            timelineEngine.validateTimeline(timelineJson);
            String styleId = payload.get("stylePresetId");
            ClipVersion version = editorEngine.saveAutosave(sessionId, timelineJson, styleId);
            return ResponseEntity.ok(Map.of("versionNumber", version.getVersionNumber(), "status", "AUTOSAVED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/checkpoint")
    public ResponseEntity<?> saveCheckpoint(
            @PathVariable("sessionId") String sessionId,
            @RequestBody Map<String, String> payload) {
        try {
            String name = payload.getOrDefault("name", "Named Checkpoint");
            String timelineJson = payload.get("timelineState");
            timelineEngine.validateTimeline(timelineJson);
            String styleId = payload.get("stylePresetId");
            ClipVersion version = editorEngine.createNamedCheckpoint(sessionId, name, timelineJson, styleId);
            return ResponseEntity.ok(Map.of("versionNumber", version.getVersionNumber(), "status", "CHECKPOINT_SAVED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/render")
    public ResponseEntity<?> dispatchRender(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("profileId") String profileId) {
        try {
            ClipVersion version = versionRepository.findLatestVersionForSession(sessionId)
                    .orElseThrow(() -> new NoSuchElementException("Latest clip version not found for session: " + sessionId));

            timelineEngine.validateTimeline(version.getTimelineStateJson());

            RenderProfile profile = profileRepository.findById(profileId)
                    .orElseGet(() -> {
                        // Seed standard profile if missing
                        RenderProfile defaultProfile = RenderProfile.builder()
                                .name("TikTok Preset")
                                .width(1080)
                                .height(1920)
                                .fps(30)
                                .videoBitrateKbps(4500)
                                .audioBitrateKbps(128)
                                .cropStrategy("CENTER")
                                .build();
                        return profileRepository.save(defaultProfile);
                    });

            RenderArtifact artifact = renderEngine.dispatchRender(version, profile);
            
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("artifactId", artifact.getId());
            res.put("status", artifact.getStatus());
            res.put("renderHash", artifact.getRenderHash());
            res.put("url", artifact.getUrl());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("Render dispatch failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/render/{artifactId}/status")
    public ResponseEntity<?> getRenderStatus(@PathVariable("artifactId") String artifactId) {
        return artifactRepository.findById(artifactId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/{sessionId}/waveform")
    public ResponseEntity<?> getWaveform(@PathVariable("sessionId") String sessionId) {
        try {
            EditSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
            String userId = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            String key = com.julius.clipper.service.StorageKeyBuilder.libraryWaveform(session.getClipId(), userId);

            if (storageClient.exists(key)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                storageClient.download(key, out);
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(out.toString(java.nio.charset.StandardCharsets.UTF_8));
            }
            return ResponseEntity.ok("[]");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/sprite")
    public ResponseEntity<?> getSpriteSheet(@PathVariable("sessionId") String sessionId) {
        try {
            EditSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
            String userId = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            String key = com.julius.clipper.service.StorageKeyBuilder.librarySprite(session.getClipId(), userId);

            if (storageClient.exists(key)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                storageClient.download(key, out);
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                        .body(out.toByteArray());
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/sprite-meta")
    public ResponseEntity<?> getSpriteMeta(@PathVariable("sessionId") String sessionId) {
        try {
            EditSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
            String userId = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            String key = com.julius.clipper.service.StorageKeyBuilder.librarySpriteMeta(session.getClipId(), userId);

            if (storageClient.exists(key)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                storageClient.download(key, out);
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(out.toString(java.nio.charset.StandardCharsets.UTF_8));
            }
            return ResponseEntity.ok("{\"interval\":2.0,\"width\":160,\"height\":90,\"columns\":10}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
