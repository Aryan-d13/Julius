package com.julius.clipper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.SubtitleStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SubtitleCompiler {
    private static final Logger log = LoggerFactory.getLogger(SubtitleCompiler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String compile(SubtitleStyle style, String timelineStateJson) throws Exception {
        log.info("Starting ASS subtitle compilation for style: {}", style.getName());

        StringBuilder ass = new StringBuilder();
        ass.append("[Script Info]\n");
        ass.append("Title: Julius Generated Subtitles\n");
        ass.append("ScriptType: v4.00+\n");
        ass.append("PlayResX: 1080\n");
        ass.append("PlayResY: 1920\n\n");

        ass.append("[V4+ Styles]\n");
        ass.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
        
        // Formulate style definition line converting CSS formats to ASS hex (BGR ordering instead of RGB)
        String assPrimary = formatColorToAss(style.getPrimaryColor());
        String assSecondary = formatColorToAss(style.getSecondaryColor());
        String assOutline = formatColorToAss(style.getOutlineColor());
        String assShadow = formatColorToAss(style.getShadowColor());

        ass.append(String.format("Style: Default,%s,%d,%s,%s,%s,%s,-1,0,0,0,100,100,0,0,1,%.1f,%.1f,%d,10,10,%d,1\n\n",
                style.getFontName(),
                style.getFontSize(),
                assPrimary,
                assSecondary,
                assOutline,
                assShadow,
                style.getOutlineWidth(),
                style.getShadowDepth(),
                style.getAlignment(),
                style.getSafeZoneVertical()
        ));

        ass.append("[Events]\n");
        ass.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        // Parse words timeline list from JSON
        List<Map<String, Object>> segments = parseTimelineSegments(timelineStateJson);
        for (Map<String, Object> segment : segments) {
            double sourceStart = ((Number) segment.getOrDefault("sourceStart", 0.0)).doubleValue();
            double duration = ((Number) segment.getOrDefault("duration", 0.0)).doubleValue();
            double timelineStart = ((Number) segment.getOrDefault("timelineStart", 0.0)).doubleValue();

            List<Map<String, Object>> words = (List<Map<String, Object>>) segment.get("words");
            if (words != null && !words.isEmpty()) {
                // Group words into short sentence blocks (e.g. 3-4 words per subtitle line)
                List<List<Map<String, Object>>> groups = groupWords(words, 4);
                for (List<Map<String, Object>> group : groups) {
                    if (group.isEmpty()) continue;
                    
                    double startSec = ((Number) group.get(0).get("start")).doubleValue() + timelineStart;
                    double endSec = ((Number) group.get(group.size() - 1).get("end")).doubleValue() + timelineStart;

                    String startStr = formatTime(startSec);
                    String endStr = formatTime(endSec);

                    // Compile dynamic highlighting using ASS Karaoke tags: {\k<centiseconds>}
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < group.size(); i++) {
                        Map<String, Object> w = group.get(i);
                        String wordText = (String) w.get("text");
                        double wStart = ((Number) w.get("start")).doubleValue();
                        double wEnd = ((Number) w.get("end")).doubleValue();
                        int centiseconds = (int) Math.max(1, Math.round((wEnd - wStart) * 100));

                        // Build active word emphasis highlighting
                        text.append(String.format("{\\k%d}%s", centiseconds, wordText));
                        if (i < group.size() - 1) {
                            text.append(" ");
                        }
                    }

                    ass.append(String.format("Dialogue: 0,%s,%s,Default,,0,0,0,,%s\n",
                            startStr, endStr, text.toString()));
                }
            }
        }

        log.info("Subtitle compilation complete.");
        return ass.toString();
    }

    private List<Map<String, Object>> parseTimelineSegments(String timelineStateJson) {
        try {
            Map<String, Object> state = objectMapper.readValue(timelineStateJson, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> tracks = (List<Map<String, Object>>) state.get("tracks");
            if (tracks != null) {
                for (Map<String, Object> track : tracks) {
                    String type = (String) track.get("type");
                    if ("SUBTITLE".equals(type) || "VIDEO".equals(type)) {
                        List<Map<String, Object>> segments = (List<Map<String, Object>>) track.get("segments");
                        if (segments != null) {
                            return segments;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse timeline state JSON: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<List<Map<String, Object>>> groupWords(List<Map<String, Object>> words, int groupSize) {
        List<List<Map<String, Object>>> groups = new ArrayList<>();
        List<Map<String, Object>> current = new ArrayList<>();
        for (Map<String, Object> word : words) {
            current.add(word);
            if (current.size() >= groupSize) {
                groups.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    private String formatTime(double seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        int s = (int) (seconds % 60);
        int cs = (int) (Math.round((seconds % 1.0) * 100));
        return String.format("%d:%02d:%02d.%02d", h, m, s, cs);
    }

    private String formatColorToAss(String rgbHex) {
        // Convert standard hex (e.g. #FFCC00 or RGBa formats) to ASS format: &H<Alpha><Blue><Green><Red>
        if (rgbHex == null || rgbHex.isBlank()) {
            return "&H00FFFFFF";
        }
        String clean = rgbHex.replace("#", "").trim();
        if (clean.length() == 6) {
            String r = clean.substring(0, 2);
            String g = clean.substring(2, 4);
            String b = clean.substring(4, 6);
            return "&H00" + b + g + r; // BGR format
        }
        return "&H00FFFFFF";
    }
}
