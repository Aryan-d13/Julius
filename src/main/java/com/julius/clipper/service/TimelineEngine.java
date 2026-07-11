package com.julius.clipper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TimelineEngine {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void validateTimeline(String timelineStateJson) throws IllegalArgumentException {
        if (timelineStateJson == null || timelineStateJson.isBlank()) {
            throw new IllegalArgumentException("Timeline state payload cannot be empty.");
        }

        try {
            Map<String, Object> state = objectMapper.readValue(timelineStateJson, new TypeReference<Map<String, Object>>() {});
            
            Number timelineDur = (Number) state.get("durationSeconds");
            if (timelineDur == null || timelineDur.doubleValue() < 0) {
                throw new IllegalArgumentException("Timeline durationSeconds cannot be negative.");
            }
            double totalDuration = timelineDur.doubleValue();

            List<Map<String, Object>> tracks = (List<Map<String, Object>>) state.get("tracks");
            if (tracks == null) {
                return;
            }

            for (Map<String, Object> track : tracks) {
                List<Map<String, Object>> segments = (List<Map<String, Object>>) track.get("segments");
                if (segments == null || segments.isEmpty()) continue;

                // Sort segments by timeline start to verify overlaps chronologically
                List<Map<String, Object>> sortedSegments = new ArrayList<>(segments);
                sortedSegments.sort(Comparator.comparingDouble(s -> ((Number) s.getOrDefault("timelineStart", 0.0)).doubleValue()));

                double lastEnd = 0.0;
                for (Map<String, Object> segment : sortedSegments) {
                    String assetId = (String) segment.get("assetId");
                    if (assetId == null || assetId.isBlank()) {
                        throw new IllegalArgumentException("Timeline segment asset reference cannot be empty.");
                    }

                    double sourceStart = ((Number) segment.getOrDefault("sourceStart", 0.0)).doubleValue();
                    double timelineStart = ((Number) segment.getOrDefault("timelineStart", 0.0)).doubleValue();
                    double duration = ((Number) segment.getOrDefault("duration", 0.0)).doubleValue();

                    if (sourceStart < 0.0) {
                        throw new IllegalArgumentException("Segment source start time cannot be negative.");
                    }
                    if (timelineStart < 0.0) {
                        throw new IllegalArgumentException("Segment timeline start time cannot be negative.");
                    }
                    if (duration <= 0.0) {
                        throw new IllegalArgumentException("Segment duration must be positive.");
                    }

                    if (timelineStart < lastEnd) {
                        throw new IllegalArgumentException(String.format("Overlapping locked segments detected on track. Segment starting at %fs overlaps with previous segment ending at %fs.", timelineStart, lastEnd));
                    }

                    if (timelineStart + duration > totalDuration + 0.01) {
                        throw new IllegalArgumentException("Segment boundaries exceed total timeline duration limits.");
                    }

                    lastEnd = timelineStart + duration;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to validate timeline state schema: " + e.getMessage(), e);
        }
    }
}
