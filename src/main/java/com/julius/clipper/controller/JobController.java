package com.julius.clipper.controller;

import com.julius.clipper.domain.Job;
import com.julius.clipper.domain.JobClip;
import com.julius.clipper.domain.JobDBStatus;
import com.julius.clipper.domain.Task;
import com.julius.clipper.domain.dto.JobConfig;
import com.julius.clipper.pipeline.QueueProvider;
import com.julius.clipper.pipeline.TaskStatus;
import com.julius.clipper.pipeline.TaskType;
import com.julius.clipper.repository.JobClipRepository;
import com.julius.clipper.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobRepository jobRepository;
    private final JobClipRepository jobClipRepository;
    private final QueueProvider queueProvider;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    public JobController(JobRepository jobRepository,
                         JobClipRepository jobClipRepository,
                         QueueProvider queueProvider,
                         RedisMessageListenerContainer redisMessageListenerContainer) {
        this.jobRepository = jobRepository;
        this.jobClipRepository = jobClipRepository;
        this.queueProvider = queueProvider;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submitJob(@RequestBody JobConfig config,
                                                         @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String jobId = UUID.randomUUID().toString();
        String userId = (headerUserId != null && !headerUserId.isBlank()) ? headerUserId : UUID.randomUUID().toString();

        log.info("Submitting new clip job config: URL={}, count={}, templateRef={}", config.getUrl(), config.getCount(), config.getTemplateRef());

        // 1. Persist new Job record
        Job job = Job.builder()
                .id(jobId)
                .userId(userId)
                .correlationId("correlation-" + UUID.randomUUID().toString().substring(0, 8))
                .config(config)
                .clipCount(config.getCount())
                .status(JobDBStatus.PENDING)
                .build();
        jobRepository.save(job);

        // 2. Build initial Task payload
        Map<String, Object> taskPayload = new HashMap<>();
        taskPayload.put("job_id", jobId);
        taskPayload.put("user_id", userId);
        taskPayload.put("url", config.getUrl());
        taskPayload.put("count", config.getCount());
        taskPayload.put("copy_language", config.getCopyLanguage() != null ? config.getCopyLanguage() : "en");
        taskPayload.put("min_duration", config.getMinDuration() > 0 ? config.getMinDuration() : 30.0);
        taskPayload.put("max_duration", config.getMaxDuration() > 0 ? config.getMaxDuration() : 900.0);

        Task downloadTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .type(TaskType.DOWNLOAD)
                .payload(taskPayload)
                .status(TaskStatus.PENDING)
                .build();

        // 3. Enqueue download task
        queueProvider.push(downloadTask);

        log.info("Job successfully submitted and enqueued initial DOWNLOAD task: jobId={}", jobId);

        Map<String, String> response = new HashMap<>();
        response.put("jobId", jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}/clips")
    public ResponseEntity<List<JobClip>> getJobClips(@PathVariable String jobId) {
        log.info("Retrieving clip fragments for jobId={}", jobId);
        List<JobClip> clips = jobClipRepository.findByJobId(jobId);
        return ResponseEntity.ok(clips);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Job> getJob(@PathVariable String jobId) {
        log.info("Retrieving job details for jobId={}", jobId);
        return jobRepository.findById(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{jobId}/stream")
    public SseEmitter streamJobEvents(@PathVariable String jobId) {
        log.info("Client initiating event stream connection for jobId={}", jobId);

        // SseEmitter with 30-minute timeout
        SseEmitter emitter = new SseEmitter(1800000L);
        ChannelTopic topic = new ChannelTopic("seone:job:" + jobId + ":events");

        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                try {
                    String jsonBody = new String(message.getBody(), StandardCharsets.UTF_8);
                    emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(jsonBody));
                } catch (Exception e) {
                    log.error("Failed to forward SSE update for jobId={}: {}", jobId, e.getMessage());
                }
            }
        };

        redisMessageListenerContainer.addMessageListener(listener, topic);
        log.info("Registered dynamic message listener on topic: {}", topic.getTopic());

        Runnable cleanup = () -> {
            redisMessageListenerContainer.removeMessageListener(listener, topic);
            log.info("Removed dynamic message listener for topic: {}", topic.getTopic());
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> {
            log.warn("SseEmitter error occurred for jobId={}: {}", jobId, t.getMessage());
            cleanup.run();
        });

        // Send confirmation event
        try {
            emitter.send(SseEmitter.event()
                    .name("subscribed")
                    .data(Map.of("message", "SSE subscription active", "jobId", jobId)));
        } catch (Exception e) {
            log.error("Failed to send initial SSE subscription confirmation: {}", e.getMessage());
        }

        return emitter;
    }
}
