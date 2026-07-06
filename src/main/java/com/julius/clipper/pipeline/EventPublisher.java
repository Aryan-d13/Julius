package com.julius.clipper.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.Job;
import com.julius.clipper.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Configuration flags
    private final boolean publishLegacyChannels = true;
    private final long maxStreamLen = 20000;

    public EventPublisher(StringRedisTemplate redisTemplate, JobRepository jobRepository) {
        this.redisTemplate = redisTemplate;
        this.jobRepository = jobRepository;
    }

    public void publish(String jobId, String userId, String eventType, Map<String, Object> payload, 
                        String step, Integer progress, String message, String traceId) {
        try {
            // 1. Resolve userId
            String resolvedUserId = userId;
            if (resolvedUserId == null || resolvedUserId.isBlank()) {
                if (payload != null && payload.containsKey("user_id") && payload.get("user_id") != null) {
                    resolvedUserId = payload.get("user_id").toString();
                } else {
                    resolvedUserId = jobRepository.findById(jobId)
                            .map(Job::getUserId)
                            .orElse("unknown");
                }
            }

            // 2. Increment sequence counter
            String seqKey = "seone:" + resolvedUserId + ":job:" + jobId + ":seq";
            Long seq = redisTemplate.opsForValue().increment(seqKey);
            if (seq == null) {
                seq = 1L;
            }

            // 3. Build event fields
            String eventId = UUID.randomUUID().toString();
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

            Map<String, String> streamFields = new HashMap<>();
            streamFields.put("event_id", eventId);
            streamFields.put("seq", seq.toString());
            streamFields.put("job_id", jobId);
            streamFields.put("user_id", resolvedUserId);
            streamFields.put("event_type", eventType);
            streamFields.put("timestamp", timestamp);
            streamFields.put("step", step != null ? step : "");
            streamFields.put("progress", progress != null ? progress.toString() : "");
            streamFields.put("message", message != null ? message : "");
            streamFields.put("payload", payload != null ? objectMapper.writeValueAsString(payload) : "{}");
            streamFields.put("trace_id", traceId != null ? traceId : "");

            // 4. XADD to stream and trim
            String streamKey = "seone:" + resolvedUserId + ":job:" + jobId + ":events";
            redisTemplate.opsForStream().add(streamKey, streamFields);
            redisTemplate.opsForStream().trim(streamKey, maxStreamLen, true); // true = approximate trim

            // 5. Dual-publish to legacy Pub/Sub channels
            if (publishLegacyChannels) {
                String envelopeJson = objectMapper.writeValueAsString(streamFields);
                redisTemplate.convertAndSend("seone:job_events", envelopeJson);
                redisTemplate.convertAndSend("seone:job:" + jobId + ":events", envelopeJson);
            }

            log.debug("Event published. Type: {}, JobId: {}, Seq: {}", eventType, jobId, seq);

        } catch (Exception e) {
            log.error("Failed to publish pipeline event for JobId: {}", jobId, e);
        }
    }
}
