package com.julius.clipper.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SegmentMerger {

    public static class MergeConfig {
        private final int silenceThresholdMs;
        private final double maxSegmentDurationS;
        private final double minSegmentDurationS;
        private final String punctuationBreak;

        public MergeConfig() {
            this.silenceThresholdMs = 700;
            this.maxSegmentDurationS = 10.0;
            this.minSegmentDurationS = 1.0;
            this.punctuationBreak = ".!?";
        }

        public MergeConfig(int silenceThresholdMs, double maxSegmentDurationS, double minSegmentDurationS, String punctuationBreak) {
            this.silenceThresholdMs = silenceThresholdMs;
            this.maxSegmentDurationS = maxSegmentDurationS;
            this.minSegmentDurationS = minSegmentDurationS;
            this.punctuationBreak = punctuationBreak;
        }

        public int getSilenceThresholdMs() {
            return silenceThresholdMs;
        }

        public double getMaxSegmentDurationS() {
            return maxSegmentDurationS;
        }

        public double getMinSegmentDurationS() {
            return minSegmentDurationS;
        }

        public String getPunctuationBreak() {
            return punctuationBreak;
        }
    }

    public static class Word {
        private String word;
        private double start;
        private double end;
        private double score;

        public Word() {}

        public Word(String word, double start, double end, double score) {
            this.word = word;
            this.start = start;
            this.end = end;
            this.score = score;
        }

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public double getStart() {
            return start;
        }

        public void setStart(double start) {
            this.start = start;
        }

        public double getEnd() {
            return end;
        }

        public void setEnd(double end) {
            this.end = end;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }

    public static class RawSegment {
        private String text;
        private double start;
        private double end;
        private List<Word> words;

        public RawSegment() {
            this.words = new ArrayList<>();
        }

        public RawSegment(String text, double start, double end, List<Word> words) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.words = words != null ? words : new ArrayList<>();
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public double getStart() {
            return start;
        }

        public void setStart(double start) {
            this.start = start;
        }

        public double getEnd() {
            return end;
        }

        public void setEnd(double end) {
            this.end = end;
        }

        public List<Word> getWords() {
            return words;
        }

        public void setWords(List<Word> words) {
            this.words = words;
        }
    }

    public static class MergedSegment {
        private final String text;
        private final double start;
        private final double end;
        private final List<Word> words;

        public MergedSegment(String text, double start, double end, List<Word> words) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.words = words;
        }

        public String getText() {
            return text;
        }

        public double getStart() {
            return start;
        }

        public double getEnd() {
            return end;
        }

        public List<Word> getWords() {
            return words;
        }
    }

    private final MergeConfig defaultConfig = new MergeConfig();

    public List<MergedSegment> merge(List<RawSegment> rawSegments, boolean includeWords) {
        return merge(rawSegments, includeWords, defaultConfig);
    }

    public List<MergedSegment> merge(List<RawSegment> rawSegments, boolean includeWords, MergeConfig config) {
        if (rawSegments == null || rawSegments.isEmpty()) {
            return Collections.emptyList();
        }

        List<MergedSegment> mergedList = new ArrayList<>();
        List<String> bufferTexts = new ArrayList<>();
        List<Word> bufferWords = new ArrayList<>();
        Double bufferStart = null;
        double bufferEnd = 0.0;

        for (RawSegment seg : rawSegments) {
            double segStart = seg.getStart();
            double segEnd = seg.getEnd();
            String segText = seg.getText() != null ? seg.getText().trim() : "";
            List<Word> segWords = seg.getWords();

            if (segText.isEmpty()) {
                continue;
            }

            double gapMs = bufferEnd > 0 ? (segStart - bufferEnd) * 1000.0 : 0.0;
            boolean shouldBreak = false;

            if (bufferStart != null) {
                double currentDuration = bufferEnd - bufferStart;

                // Rule 1: Silence gap
                if (gapMs > config.getSilenceThresholdMs()) {
                    shouldBreak = true;
                }
                // Rule 2: Max duration
                else if (currentDuration >= config.getMaxSegmentDurationS()) {
                    shouldBreak = true;
                }
                // Rule 3: Punctuation break + min duration
                else if (!bufferTexts.isEmpty() && currentDuration >= config.getMinSegmentDurationS()) {
                    String lastText = bufferTexts.get(bufferTexts.size() - 1);
                    if (!lastText.isEmpty()) {
                        char lastChar = lastText.charAt(lastText.length() - 1);
                        if (config.getPunctuationBreak().indexOf(lastChar) >= 0) {
                            shouldBreak = true;
                        }
                    }
                }
            }

            if (shouldBreak && !bufferTexts.isEmpty()) {
                mergedList.add(emitSegment(bufferTexts, bufferStart, bufferEnd, bufferWords, includeWords));
                bufferTexts.clear();
                bufferWords.clear();
                bufferStart = null;
            }

            if (bufferStart == null) {
                bufferStart = segStart;
            }

            bufferTexts.add(segText);
            bufferEnd = segEnd;

            if (includeWords && segWords != null) {
                bufferWords.addAll(segWords);
            }
        }

        if (!bufferTexts.isEmpty() && bufferStart != null) {
            mergedList.add(emitSegment(bufferTexts, bufferStart, bufferEnd, bufferWords, includeWords));
        }

        return mergedList;
    }

    private MergedSegment emitSegment(List<String> texts, double start, double end, List<Word> words, boolean includeWords) {
        String mergedText = String.join(" ", texts);
        double roundedStart = Math.round(start * 100.0) / 100.0;
        double roundedEnd = Math.round(end * 100.0) / 100.0;
        List<Word> wordsList = includeWords ? new ArrayList<>(words) : null;
        return new MergedSegment(mergedText, roundedStart, roundedEnd, wordsList);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> mergeMaps(List<Map<String, Object>> rawSegments, boolean includeWords) {
        if (rawSegments == null || rawSegments.isEmpty()) {
            return Collections.emptyList();
        }
        List<RawSegment> rawList = new ArrayList<>();
        for (Map<String, Object> map : rawSegments) {
            double start = getDouble(map, "start", 0.0);
            double end = getDouble(map, "end", 0.0);
            String text = getString(map, "text", "");
            List<Word> words = new ArrayList<>();
            List<Map<String, Object>> rawWords = (List<Map<String, Object>>) map.get("words");
            if (rawWords != null) {
                for (Map<String, Object> wMap : rawWords) {
                    words.add(new Word(
                            getString(wMap, "word", ""),
                            getDouble(wMap, "start", 0.0),
                            getDouble(wMap, "end", 0.0),
                            getDouble(wMap, "score", 0.0)
                    ));
                }
            }
            rawList.add(new RawSegment(text, start, end, words));
        }
        
        List<MergedSegment> mergedList = merge(rawList, includeWords);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MergedSegment seg : mergedList) {
            Map<String, Object> segMap = new HashMap<>();
            segMap.put("text", seg.getText());
            segMap.put("start", seg.getStart());
            segMap.put("end", seg.getEnd());
            if (includeWords && seg.getWords() != null) {
                List<Map<String, Object>> wordsList = new ArrayList<>();
                for (Word w : seg.getWords()) {
                    Map<String, Object> wMap = new HashMap<>();
                    wMap.put("word", w.getWord());
                    wMap.put("start", w.getStart());
                    wMap.put("end", w.getEnd());
                    wMap.put("score", w.getScore());
                    wordsList.add(wMap);
                }
                segMap.put("words", wordsList);
            }
            result.add(segMap);
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
