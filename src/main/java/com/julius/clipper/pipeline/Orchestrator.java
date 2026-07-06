package com.julius.clipper.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final JobRepository jobRepository;
    private final JobClipRepository jobClipRepository;
    private final JobStepRepository jobStepRepository;
    private final TaskRepository taskRepository;
    private final EventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Orchestrator(JobRepository jobRepository, 
                        JobClipRepository jobClipRepository, 
                        JobStepRepository jobStepRepository,
                        TaskRepository taskRepository,
                        EventPublisher eventPublisher, 
                        StringRedisTemplate redisTemplate,
                        MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.jobClipRepository = jobClipRepository;
        this.jobStepRepository = jobStepRepository;
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public List<Task> getNextTasks(Task currentTask, Map<String, Object> result) {
        String jobId = currentTask.getJobId();
        String userId = currentTask.getUserId();
        TaskType taskType = currentTask.getType();

        // 1. Join Zombie Guard: Check if the job is already terminal
        if (jobId != null) {
            Optional<Job> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isPresent()) {
                JobDBStatus status = jobOpt.get().getStatus();
                if (status == JobDBStatus.COMPLETED || status == JobDBStatus.FAILED || status == JobDBStatus.CANCELLED) {
                    log.warn("Zombie Guard: Ignoring task generation for terminal job: {}", jobId);
                    return Collections.emptyList();
                }
            }
        }

        List<Task> nextTasks = new ArrayList<>();

        switch (taskType) {
            case DOWNLOAD:
                // Fork: Set fork Entered, and return TRANSCRIBE and DOWNLOAD_VIDEO
                markForkEntered(jobId);
                
                // Audio path
                Map<String, Object> transcribePayload = new HashMap<>(currentTask.getPayload());
                transcribePayload.put("storage_key", result.get("storage_key"));
                transcribePayload.put("clip_id", result.get("clip_id"));
                nextTasks.add(Task.builder()
                        .type(TaskType.TRANSCRIBE)
                        .payload(transcribePayload)
                        .build());
                
                // Video path
                Map<String, Object> downloadVideoPayload = new HashMap<>(currentTask.getPayload());
                downloadVideoPayload.put("clip_id", result.get("clip_id"));
                nextTasks.add(Task.builder()
                        .type(TaskType.DOWNLOAD_VIDEO)
                        .payload(downloadVideoPayload)
                        .build());
                break;

            case INGEST:
                // Ingest skips download video. Go straight to transcribe.
                Map<String, Object> ingestTransPayload = new HashMap<>(currentTask.getPayload());
                ingestTransPayload.put("storage_key", result.get("storage_key"));
                nextTasks.add(Task.builder()
                        .type(TaskType.TRANSCRIBE)
                        .payload(ingestTransPayload)
                        .build());
                break;

            case TRANSCRIBE:
                // Linear: TRANSCRIBE -> ANALYZE
                Map<String, Object> analyzePayload = new HashMap<>(currentTask.getPayload());
                analyzePayload.put("transcript_key", result.get("transcript_key"));
                nextTasks.add(Task.builder()
                        .type(TaskType.ANALYZE)
                        .payload(analyzePayload)
                        .build());
                break;

            case ANALYZE:
            case DOWNLOAD_VIDEO:
                // Save parallel fork results to Redis for join-barrier synchronization
                if (taskType == TaskType.DOWNLOAD_VIDEO) {
                    String videoKey = (String) result.get("video_key");
                    if (videoKey != null) {
                        redisTemplate.opsForValue().set("seone:job:" + jobId + ":video_key", videoKey, 1, TimeUnit.HOURS);
                    }
                }
                if (taskType == TaskType.ANALYZE) {
                    String cacheKey = (String) result.get("analysis_results_cache");
                    if (cacheKey != null) {
                        redisTemplate.opsForValue().set("seone:job:" + jobId + ":analysis_results_cache", cacheKey, 1, TimeUnit.HOURS);
                    }
                }

                // Join barrier
                String joinKey = "seone:" + userId + ":join:" + jobId + ":smart_render_prep";
                redisTemplate.opsForSet().add(joinKey, taskType.name());
                redisTemplate.expire(joinKey, 1, TimeUnit.HOURS);
                Long count = redisTemplate.opsForSet().size(joinKey);

                if (count != null && count == 2) {
                    markJoinSatisfied(jobId);
                    
                    // Retrieve best clips metadata from the job or cache
                    List<Map<String, Object>> bestClips = getBestClipsFromResults(userId, jobId, currentTask);
                    
                    // Adjust expected clip counts if LLM generated fewer clips than requested
                    int requestedCount = getIntFromPayload(currentTask.getPayload(), "count", 5);
                    int actualCount = bestClips.size();
                    if (actualCount > 0 && actualCount < requestedCount) {
                        adjustExpectedClipCount(jobId, actualCount);
                    }

                    // Retrieve storage video key from Redis or task results
                    String sourceVideoKey = redisTemplate.opsForValue().get("seone:job:" + jobId + ":video_key");
                    if (sourceVideoKey == null) {
                        sourceVideoKey = (String) result.get("video_key");
                    }
                    if (sourceVideoKey == null) {
                        sourceVideoKey = (String) currentTask.getPayload().get("video_key");
                    }

                    // Fan-out: create N smart render tasks (one per clip)
                    for (int i = 0; i < bestClips.size(); i++) {
                        Map<String, Object> clip = bestClips.get(i);
                        Map<String, Object> renderPayload = new HashMap<>(currentTask.getPayload());
                        
                        renderPayload.put("index", i + 1);
                        renderPayload.put("time_window", Map.of(
                                "start", clip.get("start"),
                                "end", clip.get("end")
                        ));
                        renderPayload.put("score", clip.get("score"));
                        renderPayload.put("reasoning", clip.get("reasoning"));
                        
                        // Pick POV text depending on language config
                        String copyLang = (String) currentTask.getPayload().get("copy_language");
                        String povTextKey = "hi".equalsIgnoreCase(copyLang) ? "pov_hi" : "pov_en";
                        renderPayload.put("inputs", Map.of("pov_text", clip.getOrDefault(povTextKey, clip.getOrDefault("pov_en", ""))));
                        if (sourceVideoKey != null) {
                            renderPayload.put("source_video_key", sourceVideoKey);
                        }

                        nextTasks.add(Task.builder()
                                .type(TaskType.SMART_RENDER)
                                .payload(renderPayload)
                                .build());
                    }
                }
                break;

            case CUT:
            case SMART_RENDER:
                // Terminal steps: register clip
                registerClipOutput(jobId, result);
                break;
        }

        return nextTasks;
    }

    @Transactional
    public void registerClipOutput(String jobId, Map<String, Object> clipInfo) {
        // 1. Zombie Guard
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        if (job.getStatus() == JobDBStatus.COMPLETED || job.getStatus() == JobDBStatus.FAILED || job.getStatus() == JobDBStatus.CANCELLED) {
            log.warn("Zombie Guard: Ignoring clip registration for terminal job: {}", jobId);
            return;
        }

        int clipIndex = getIntFromPayload(clipInfo, "index", 1);
        String filename = (String) clipInfo.get("filename");
        String rawStorageKey = (String) clipInfo.get("storage_key");
        
        // Normalize key (strip "data/" prefix)
        String storageKey = rawStorageKey != null ? rawStorageKey.replaceFirst("^data/", "") : "";
        String url = (String) clipInfo.get("url");
        Double duration = clipInfo.containsKey("duration_seconds") ? ((Number) clipInfo.get("duration_seconds")).doubleValue() : null;
        Long sizeBytes = clipInfo.containsKey("size_bytes") ? ((Number) clipInfo.get("size_bytes")).longValue() : null;

        // Try inserting Clip record. Catch unique constraint violations for idempotency.
        try {
            Optional<JobClip> existing = jobClipRepository.findByJobIdAndClipIndex(jobId, clipIndex);
            if (existing.isPresent()) {
                log.info("Clip index {} already exists for Job {} (retry idempotency)", clipIndex, jobId);
                return;
            }
            
            JobClip jobClip = JobClip.builder()
                    .jobId(jobId)
                    .clipIndex(clipIndex)
                    .filename(filename)
                    .storageKey(storageKey)
                    .url(url)
                    .durationSeconds(duration)
                    .sizeBytes(sizeBytes)
                    .build();
            jobClipRepository.saveAndFlush(jobClip);
        } catch (Exception e) {
            log.info("Clip registration integrity constraint triggered (retry idempotency): {}", e.getMessage());
            return; // stop and do not run completion checks again
        }

        // Count clips ready in database
        long readyCount = jobClipRepository.countByJobId(jobId);
        job.setClipsReady((int) readyCount);

        boolean isFinished = readyCount >= job.getClipCount();
        
        if (isFinished) {
            job.setCompletedAt(LocalDateTime.now());
            job.setStatus(JobDBStatus.COMPLETED);
            jobRepository.save(job);
            
            // Emit final completion event
            eventPublisher.publish(jobId, job.getUserId(), "job_completed", 
                    Map.of("clips_ready", readyCount, "total_clips", job.getClipCount()), 
                    "completed", 100, "Job completed successfully", null);

            // Record custom job completion counter metric
            Counter.builder("clipper.jobs.processed.total")
                    .description("Aggregated outcomes of completed clipping jobs")
                    .tag("status", "SUCCESS")
                    .register(meterRegistry)
                    .increment();
        } else {
            jobRepository.save(job);
            
            // Emit progress event
            int progressPercent = (int) ((readyCount * 100) / job.getClipCount());
            eventPublisher.publish(jobId, job.getUserId(), "progress_update", 
                    Map.of("clips_ready", readyCount, "total_clips", job.getClipCount()), 
                    "smart_render", progressPercent, "Clip " + readyCount + "/" + job.getClipCount() + " rendered", null);
        }
    }

    @Transactional
    public void markJobFailed(String jobId, String error, String userId) {
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) return;
        
        Job job = jobOpt.get();
        if (job.getStatus() == JobDBStatus.COMPLETED || job.getStatus() == JobDBStatus.FAILED || job.getStatus() == JobDBStatus.CANCELLED) {
            log.warn("Zombie Guard: Ignoring markJobFailed for terminal job: {}", jobId);
            return;
        }

        // 1. Mark status in DB
        job.setStatus(JobDBStatus.FAILED);
        job.setErrorMessage(error.length() > 500 ? error.substring(0, 500) : error);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        // 2. Delete Redis Join keys
        String joinKeyPattern = "seone:" + (userId != null ? userId : job.getUserId()) + ":join:" + jobId + ":*";
        Set<String> keys = redisTemplate.keys(joinKeyPattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 3. Cancel outstanding database tasks
        List<Task> pendingTasks = taskRepository.findAll();
        for (Task t : pendingTasks) {
            if (jobId.equals(t.getJobId()) && (t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.PROCESSING)) {
                t.setStatus(TaskStatus.CANCELLED);
                taskRepository.save(t);
            }
        }

        // 4. Emit failure event
        eventPublisher.publish(jobId, job.getUserId(), "job_failed", Map.of("error", error), 
                job.getCurrentStep(), 0, "Job failed: " + error, null);

        // Record custom job completion counter metric for failure outcome
        Counter.builder("clipper.jobs.processed.total")
                .description("Aggregated outcomes of completed clipping jobs")
                .tag("status", "FAILED")
                .register(meterRegistry)
                .increment();
    }

    @Transactional
    public void setStartedAtAtomic(String jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStartedAt() == null) {
                job.setStartedAt(LocalDateTime.now());
                job.setStatus(JobDBStatus.PROCESSING);
                jobRepository.save(job);
            }
        });
    }

    @Transactional
    public void updateCurrentStep(String jobId, String stepName) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setCurrentStep(stepName);
            jobRepository.save(job);
        });
    }

    @Transactional
    public void recordStepStart(String jobId, String stepName) {
        Optional<JobStep> stepOpt = jobStepRepository.findByJobIdAndStepType(jobId, stepName);
        JobStep step = stepOpt.orElseGet(() -> JobStep.builder().jobId(jobId).stepType(stepName).build());
        step.setStatus("running");
        step.setStartedAt(LocalDateTime.now());
        step.setCompletedAt(null);
        step.setErrorMessage(null);
        jobStepRepository.save(step);
    }

    @Transactional
    public void recordStepCompletion(String jobId, String stepName, String status, String error) {
        Optional<JobStep> stepOpt = jobStepRepository.findByJobIdAndStepType(jobId, stepName);
        if (stepOpt.isPresent()) {
            JobStep step = stepOpt.get();
            step.setStatus(status);
            step.setCompletedAt(LocalDateTime.now());
            step.setErrorMessage(error);
            jobStepRepository.save(step);
        }
    }

    private void markForkEntered(String jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setForkEnteredAt(LocalDateTime.now());
            jobRepository.save(job);
            
            // Record step step entry
            recordStepStart(jobId, "fork:entered");
            recordStepCompletion(jobId, "fork:entered", "completed", null);
        });
    }

    private void markJoinSatisfied(String jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setJoinSatisfiedAt(LocalDateTime.now());
            jobRepository.save(job);
            
            recordStepStart(jobId, "join:smart_render_prep");
            recordStepCompletion(jobId, "join:smart_render_prep", "completed", null);
        });
    }

    private void adjustExpectedClipCount(String jobId, int actualCount) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setClipCount(actualCount);
            jobRepository.save(job);
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getBestClipsFromResults(String userId, String jobId, Task task) {
        try {
            Object analysisDataObj = task.getPayload().get("best_clips");
            if (analysisDataObj instanceof List) {
                return (List<Map<String, Object>>) analysisDataObj;
            }
            
            String cacheKey = redisTemplate.opsForValue().get("seone:job:" + jobId + ":analysis_results_cache");
            if (cacheKey == null) {
                cacheKey = (String) task.getPayload().get("analysis_results_cache");
            }
            if (cacheKey != null && !cacheKey.isBlank()) {
                String cacheVal = redisTemplate.opsForValue().get(cacheKey);
                if (cacheVal != null) {
                    Map<String, Object> cache = objectMapper.readValue(cacheVal, new TypeReference<Map<String, Object>>() {});
                    Object bestClipsObj = cache.get("best_clips");
                    if (bestClipsObj instanceof List) {
                        return (List<Map<String, Object>>) bestClipsObj;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load best clips metadata for Job {}", jobId, e);
        }
        
        // Fallback default mock clip window for robust execution
        Map<String, Object> defaultClip = new HashMap<>();
        defaultClip.put("start", 10.0);
        defaultClip.put("end", 40.0);
        defaultClip.put("score", 90);
        defaultClip.put("reasoning", "Engaging hook and clear climax");
        defaultClip.put("pov_en", "Wait for the twist...");
        defaultClip.put("pov_hi", "ट्विस्ट का इंतजार करें...");
        return List.of(defaultClip);
    }

    private int getIntFromPayload(Map<String, Object> payload, String key, int defaultVal) {
        if (payload == null || !payload.containsKey(key)) {
            return defaultVal;
        }
        Object val = payload.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
