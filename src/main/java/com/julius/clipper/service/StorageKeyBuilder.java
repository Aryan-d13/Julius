package com.julius.clipper.service;

public class StorageKeyBuilder {

    public static String rawAudio(String clipId) {
        validateId(clipId);
        return "raw/audio_" + clipId + ".wav";
    }

    public static String rawVideo(String clipId) {
        validateId(clipId);
        return "raw/video_" + clipId + ".mp4";
    }

    public static String jobClip(String jobId, int index, String templateRef) {
        validateId(jobId);
        String cleanTemplate = templateRef != null ? templateRef.replaceAll("[^a-zA-Z0-9_-]", "") : "default";
        return "jobs/" + jobId + "/clips/clip_" + index + "_" + cleanTemplate + ".mp4";
    }

    public static String jobTranscript(String jobId, String clipId) {
        validateId(jobId);
        validateId(clipId);
        return "jobs/" + jobId + "/transcripts/transcript_" + clipId + ".json";
    }

    public static String cacheAnalysis(String clipId) {
        validateId(clipId);
        return "cache/analysis_" + clipId + ".json";
    }

    public static String libraryVideo(String clipId, String originalExtension, String userId) {
        validateId(clipId);
        String prefix = userId != null ? userId + "_" : "";
        return "library/videos/" + prefix + clipId + originalExtension;
    }

    public static String libraryAudio(String clipId, String userId) {
        validateId(clipId);
        String prefix = userId != null ? userId + "_" : "";
        return "library/audios/" + prefix + clipId + ".wav";
    }

    public static String thumbnail(int index) {
        return "thumbnails/clip_" + index + ".jpg";
    }

    private static void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID cannot be null or empty for storage key building");
        }
        // Basic path traversal guard
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new IllegalArgumentException("ID contains invalid characters for namespace key building: " + id);
        }
    }
}
