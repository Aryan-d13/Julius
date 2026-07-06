package com.julius.clipper.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SlidingWindowSelector {

    public static class Chunk {
        private final double start;
        private final double end;
        private final double score;
        private final String text;

        public Chunk(double start, double end, double score, String text) {
            this.start = start;
            this.end = end;
            this.score = score;
            this.text = text != null ? text : "";
        }

        public double getStart() {
            return start;
        }

        public double getEnd() {
            return end;
        }

        public double getScore() {
            return score;
        }

        public String getText() {
            return text;
        }
    }

    public static class Window {
        private final double start;
        private final double end;
        private final double score;
        private final String text;
        private final int startIndex;
        private final int endIndex;

        public Window(double start, double end, double score, String text, int startIndex, int endIndex) {
            this.start = start;
            this.end = end;
            this.score = score;
            this.text = text;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        public double getStart() {
            return start;
        }

        public double getEnd() {
            return end;
        }

        public double getScore() {
            return score;
        }

        public String getText() {
            return text;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }
    }

    private final double minDuration;
    private final double maxDuration;

    public SlidingWindowSelector() {
        this.minDuration = 60.0;
        this.maxDuration = 300.0;
    }

    public SlidingWindowSelector(double minDuration, double maxDuration) {
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
    }

    public Window findBestWindow(List<Chunk> chunks) {
        return findBestWindowExcluding(chunks, Collections.emptySet());
    }

    public List<Window> findTopNWindows(List<Chunk> chunks, int n) {
        if (chunks == null || chunks.isEmpty() || n <= 0) {
            return Collections.emptyList();
        }

        Set<Integer> usedIndices = new HashSet<>();
        List<Window> topWindows = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Window bestWindow = findBestWindowExcluding(chunks, usedIndices);
            if (bestWindow == null) {
                break;
            }
            topWindows.add(bestWindow);
            
            for (int k = bestWindow.getStartIndex(); k <= bestWindow.getEndIndex(); k++) {
                usedIndices.add(k);
            }
        }

        return topWindows;
    }

    private Window findBestWindowExcluding(List<Chunk> chunks, Set<Integer> excludedIndices) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }

        Window bestWindow = null;
        double maxAvgScore = -1.0;
        int len = chunks.size();

        for (int i = 0; i < len; i++) {
            if (excludedIndices.contains(i)) {
                continue;
            }

            double currentDuration = 0.0;
            double currentScoreSum = 0.0;
            List<String> windowText = new ArrayList<>();

            for (int j = i; j < len; j++) {
                if (excludedIndices.contains(j)) {
                    break; // Window broken by used chunk
                }

                Chunk chunk = chunks.get(j);
                double chunkDuration = chunk.getEnd() - chunk.getStart();
                
                if (currentDuration + chunkDuration > maxDuration) {
                    break;
                }

                currentDuration += chunkDuration;
                currentScoreSum += chunk.getScore();
                windowText.add(chunk.getText());

                if (currentDuration >= minDuration) {
                    double avgScore = currentScoreSum / (j - i + 1);
                    if (avgScore > maxAvgScore) {
                        maxAvgScore = avgScore;
                        
                        double windowStart = chunks.get(i).getStart();
                        double windowEnd = chunk.getEnd();
                        String mergedText = String.join(" ", windowText);
                        
                        bestWindow = new Window(windowStart, windowEnd, avgScore, mergedText, i, j);
                    }
                }
            }
        }
        return bestWindow;
    }

    public List<Map<String, Object>> findTopNWindowsMaps(List<Map<String, Object>> chunks, int n) {
        if (chunks == null || chunks.isEmpty() || n <= 0) {
            return Collections.emptyList();
        }
        List<Chunk> chunkList = new ArrayList<>();
        for (Map<String, Object> map : chunks) {
            chunkList.add(new Chunk(
                    getDouble(map, "start", 0.0),
                    getDouble(map, "end", 0.0),
                    getDouble(map, "score", 0.0),
                    getString(map, "text", "")
            ));
        }
        List<Window> windowList = findTopNWindows(chunkList, n);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Window w : windowList) {
            Map<String, Object> wMap = new HashMap<>();
            wMap.put("start", w.getStart());
            wMap.put("end", w.getEnd());
            wMap.put("score", w.getScore());
            wMap.put("text", w.getText());
            result.add(wMap);
        }
        return result;
    }

    private double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return defaultVal;
    }

    private String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }
}
