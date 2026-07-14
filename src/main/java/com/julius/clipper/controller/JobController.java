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
import com.julius.clipper.repository.WorkspaceRepository;
import com.julius.clipper.service.BillingService;
import com.julius.clipper.domain.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobRepository jobRepository;
    private final JobClipRepository jobClipRepository;
    private final QueueProvider queueProvider;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final WorkspaceRepository workspaceRepository;
    private final BillingService billingService;

    public JobController(JobRepository jobRepository,
                         JobClipRepository jobClipRepository,
                         QueueProvider queueProvider,
                         RedisMessageListenerContainer redisMessageListenerContainer,
                         WorkspaceRepository workspaceRepository,
                         BillingService billingService) {
        this.jobRepository = jobRepository;
        this.jobClipRepository = jobClipRepository;
        this.queueProvider = queueProvider;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
        this.workspaceRepository = workspaceRepository;
        this.billingService = billingService;
    }

    @PostMapping
    @PreAuthorize("hasPermission(#workspaceId, 'WORKSPACE', 'jobs.create')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> submitJob(
            @PathVariable String workspaceId,
            @RequestBody JobConfig config) {
        try {
            Workspace ws = workspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
            String orgId = ws.getOrganization().getId();

            // Perform CAS quota verification
            billingService.consumeQuota(orgId, "RENDER_JOBS", 1.0);

            String jobId = UUID.randomUUID().toString();
            String userId = SecurityContextHolder.getContext().getAuthentication().getName();
            String correlationId = org.slf4j.MDC.get(com.julius.clipper.telemetry.CorrelationFilter.CORRELATION_ID_MDC_KEY);
            String requestId = org.slf4j.MDC.get(com.julius.clipper.telemetry.CorrelationFilter.REQUEST_ID_MDC_KEY);
            
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = "corr-" + UUID.randomUUID().toString().substring(0, 8);
            }
            if (requestId == null || requestId.isBlank()) {
                requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
            }

            log.info("Submitting new clip job config in workspace {}: URL={}, count={}", workspaceId, config.getUrl(), config.getCount());

            // Persist new Job record linked to workspace and creator
            Job job = Job.builder()
                    .id(jobId)
                    .userId(userId)
                    .workspaceId(workspaceId)
                    .createdByUserId(userId)
                    .correlationId(correlationId)
                    .config(config)
                    .clipCount(config.getCount())
                    .status(JobDBStatus.PENDING)
                    .build();
            jobRepository.save(job);

            // Build initial Task payload
            Map<String, Object> taskPayload = new HashMap<>();
            taskPayload.put("job_id", jobId);
            taskPayload.put("user_id", userId);
            taskPayload.put("workspace_id", workspaceId);
            taskPayload.put("url", config.getUrl());
            taskPayload.put("count", config.getCount());
            taskPayload.put("copy_language", config.getCopyLanguage() != null ? config.getCopyLanguage() : "en");
            taskPayload.put("min_duration", config.getMinDuration() > 0 ? config.getMinDuration() : 30.0);
            taskPayload.put("max_duration", config.getMaxDuration() > 0 ? config.getMaxDuration() : 900.0);

            Map<String, Object> taskMetadata = new HashMap<>();
            taskMetadata.put("correlation_id", correlationId);
            taskMetadata.put("request_id", requestId);

            Task downloadTask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .type(TaskType.DOWNLOAD)
                    .payload(taskPayload)
                    .metadata(taskMetadata)
                    .status(TaskStatus.PENDING)
                    .build();

            queueProvider.push(downloadTask);

            log.info("Job successfully submitted and enqueued initial DOWNLOAD task: jobId={}", jobId);

            Map<String, String> response = new HashMap<>();
            response.put("jobId", jobId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (BillingService.QuotaExceededException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{jobId}/clips")
    @PreAuthorize("hasPermission(#workspaceId, 'WORKSPACE', 'jobs.share')")
    public ResponseEntity<List<JobClip>> getJobClips(
            @PathVariable String workspaceId,
            @PathVariable String jobId) {
        log.info("Retrieving clip fragments for jobId={} in workspace {}", jobId, workspaceId);
        List<JobClip> clips = jobClipRepository.findByJobId(jobId);
        return ResponseEntity.ok(clips);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasPermission(#workspaceId, 'WORKSPACE', 'jobs.share')")
    public ResponseEntity<Job> getJob(
            @PathVariable String workspaceId,
            @PathVariable String jobId) {
        log.info("Retrieving job details for jobId={} in workspace {}", jobId, workspaceId);
        return jobRepository.findById(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{jobId}/stream")
    @PreAuthorize("hasPermission(#workspaceId, 'WORKSPACE', 'jobs.share')")
    public SseEmitter streamJobEvents(
            @PathVariable String workspaceId,
            @PathVariable String jobId) {
        log.info("Client initiating event stream connection for jobId={} in workspace {}", jobId, workspaceId);

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
