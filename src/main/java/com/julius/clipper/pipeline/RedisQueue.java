package com.julius.clipper.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.Task;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisQueue implements QueueProvider {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public RedisQueue(StringRedisTemplate redisTemplate, io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void push(Task task) {
        if (task.getId() == null) {
            task.setId(UUID.randomUUID().toString());
        }
        String taskId = task.getId();
        String userId = task.getUserId();
        TaskType taskType = task.getType();

        io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                .tag("operation", "push")
                .tag("task_type", taskType.name())
                .register(meterRegistry)
                .increment();

        try {
            // 1. Serialize and save the Task to HASH
            String taskJson = objectMapper.writeValueAsString(task);
            String taskHashKey = "seone:task:" + taskId;
            
            Map<String, String> hash = new HashMap<>();
            hash.put("payload", taskJson);
            hash.put("user_id", userId != null ? userId : "");
            hash.put("type", taskType.name());
            hash.put("status", TaskStatus.PENDING.name());
            hash.put("created_at", String.valueOf(Instant.now().getEpochSecond()));
            
            redisTemplate.opsForHash().putAll(taskHashKey, hash);
            redisTemplate.expire(taskHashKey, 7, TimeUnit.DAYS);

            // 2. LPUSH to appropriate queue keys
            if (userId != null && !userId.isBlank()) {
                String queueKey = "seone:" + userId + ":queue:" + taskType.name();
                redisTemplate.opsForList().leftPush(queueKey, taskId);
                
                // Add to active tenants set
                redisTemplate.opsForSet().add("seone:active_tenants", userId);
                
                // Add KEDA pending signal
                redisTemplate.opsForList().leftPush("seone:pending_signal:" + taskType.name(), taskId);
            } else {
                // Legacy / fallback queue push
                String legacyQueueKey = "seone:queue:" + taskType.name();
                redisTemplate.opsForList().leftPush(legacyQueueKey, taskId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to push task to Redis queue", e);
        }
    }

    @Override
    public Task pop(TaskType taskType) {
        String taskTypeName = taskType.name();

        // 1. Fetch active tenants and shuffle for fair polling
        Set<String> activeTenantsSet = redisTemplate.opsForSet().members("seone:active_tenants");
        if (activeTenantsSet != null && !activeTenantsSet.isEmpty()) {
            List<String> tenants = new ArrayList<>(activeTenantsSet);
            Collections.shuffle(tenants);

            for (String tenantId : tenants) {
                String queueKey = "seone:" + tenantId + ":queue:" + taskTypeName;
                String processingKey = "seone:" + tenantId + ":processing:" + taskTypeName;

                // Atomic pop-push migration
                String taskId = redisTemplate.opsForList().rightPopAndLeftPush(queueKey, processingKey);
                if (taskId != null) {
                    // Update signals
                    redisTemplate.opsForList().remove("seone:pending_signal:" + taskTypeName, 0, taskId);
                    redisTemplate.opsForList().leftPush("seone:processing_signal:" + taskTypeName, taskId);
                    
                    io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                            .tag("operation", "pop")
                            .tag("task_type", taskTypeName)
                            .register(meterRegistry)
                            .increment();

                    return hydrateTask(taskId);
                }
            }
        }

        // 2. Legacy fallback pop
        String legacyQueueKey = "seone:queue:" + taskTypeName;
        String legacyProcessingKey = "seone:processing:" + taskTypeName;
        String taskId = redisTemplate.opsForList().rightPopAndLeftPush(legacyQueueKey, legacyProcessingKey);
        if (taskId != null) {
            io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                    .tag("operation", "pop")
                    .tag("task_type", taskTypeName)
                    .register(meterRegistry)
                    .increment();

            return hydrateTask(taskId);
        }

        return null;
    }

    @Override
    public void complete(Task task) {
        String taskId = task.getId();
        String userId = task.getUserId();
        String taskTypeName = task.getType().name();

        String processingKey;
        if (userId != null && !userId.isBlank()) {
            processingKey = "seone:" + userId + ":processing:" + taskTypeName;
        } else {
            processingKey = "seone:processing:" + taskTypeName;
        }

        // Remove from processing queue list
        redisTemplate.opsForList().remove(processingKey, 0, taskId);
        
        // Remove from processing signal
        redisTemplate.opsForList().remove("seone:processing_signal:" + taskTypeName, 0, taskId);

        // Delete the task hash details
        redisTemplate.delete("seone:task:" + taskId);

        io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                .tag("operation", "complete")
                .tag("task_type", taskTypeName)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void fail(String taskId, String error) {
        String taskHashKey = "seone:task:" + taskId;
        
        // Get details from task hash
        String userId = (String) redisTemplate.opsForHash().get(taskHashKey, "user_id");
        String typeVal = (String) redisTemplate.opsForHash().get(taskHashKey, "type");
        
        String taskTypeName = typeVal != null ? typeVal : "UNKNOWN";
        String processingKey = (userId != null && !userId.isBlank())
                ? "seone:" + userId + ":processing:" + taskTypeName
                : "seone:processing:" + taskTypeName;

        // Remove from processing lists
        redisTemplate.opsForList().remove(processingKey, 0, taskId);
        redisTemplate.opsForList().remove("seone:processing_signal:" + taskTypeName, 0, taskId);

        // Create DLQ entry
        try {
            Map<String, Object> dlqEntry = new HashMap<>();
            dlqEntry.put("id", taskId);
            dlqEntry.put("error", error);
            dlqEntry.put("user_id", userId);
            dlqEntry.put("failed_at", Instant.now().getEpochSecond());
            dlqEntry.put("failed_at_iso", LocalDateTime.now().toString());

            String dlqJson = objectMapper.writeValueAsString(dlqEntry);
            String dlqKey = (userId != null && !userId.isBlank())
                    ? "seone:" + userId + ":dlq"
                    : "seone:dlq";

            redisTemplate.opsForList().rightPush(dlqKey, dlqJson);
            redisTemplate.opsForList().trim(dlqKey, 0, 9999); // LTRIM to DLQ_MAX_LENGTH (10000)
        } catch (Exception e) {
            // Log fallback fail
        }

        // Delete original task hash
        redisTemplate.delete(taskHashKey);

        io.micrometer.core.instrument.Counter.builder("clipper.queue.operations")
                .tag("operation", "fail")
                .tag("task_type", taskTypeName)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void touchTaskHeartbeat(String taskId) {
        String taskHashKey = "seone:task:" + taskId;
        redisTemplate.opsForHash().put(taskHashKey, "heartbeat_at", String.valueOf(Instant.now().getEpochSecond()));
    }

    private Task hydrateTask(String taskId) {
        String taskHashKey = "seone:task:" + taskId;
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(taskHashKey);
        if (hash == null || hash.isEmpty()) {
            return null;
        }

        try {
            String payloadJson = (String) hash.get("payload");
            Task task = objectMapper.readValue(payloadJson, Task.class);
            
            // Sync status from hash if it changed
            String statusName = (String) hash.get("status");
            if (statusName != null) {
                task.setStatus(TaskStatus.valueOf(statusName));
            }
            
            String startedAtVal = (String) hash.get("started_at");
            if (startedAtVal != null) {
                task.setStartedAt(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(Long.parseLong(startedAtVal)), 
                        ZoneId.systemDefault()
                ));
            }

            return task;
        } catch (Exception e) {
            throw new RuntimeException("Failed to hydrate Task from Redis hash", e);
        }
    }

    @Override
    public long getQueueDepth(TaskType taskType) {
        String pendingSignalKey = "seone:pending_signal:" + taskType.name();
        String legacyKey = "seone:queue:" + taskType.name();
        try {
            Long pendingSignalSize = redisTemplate.opsForList().size(pendingSignalKey);
            Long legacySize = redisTemplate.opsForList().size(legacyKey);
            long total = 0;
            if (pendingSignalSize != null) total += pendingSignalSize;
            if (legacySize != null) total += legacySize;
            return total;
        } catch (Exception e) {
            return 0L;
        }
    }
}
