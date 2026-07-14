package com.julius.clipper.service;

import com.julius.clipper.domain.OutboxEvent;
import com.julius.clipper.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@EnableScheduling
public class OutboxProcessor {
    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventService outboxEventService;

    public OutboxProcessor(
            OutboxEventRepository outboxEventRepository,
            OutboxEventService outboxEventService) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventService = outboxEventService;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processPendingEvents() {
        // Fetch and lock pending outbox events using SELECT FOR UPDATE SKIP LOCKED
        // Process up to 10 events per poller run to prevent long-running transactions
        List<OutboxEvent> pending = outboxEventRepository.findPendingForUpdateSkipLocked(
                "PENDING", 
                PageRequest.of(0, 10)
        );
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events locked for processing.", pending.size());
        for (OutboxEvent event : pending) {
            try {
                // Call the separate bean's REQUIRES_NEW transactional method to prevent self-invocation bypasses
                outboxEventService.processSingleEvent(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event: " + event.getId(), e);
                try {
                    outboxEventService.markEventFailed(event);
                } catch (Exception ex) {
                    log.error("Failed to mark event failed: " + event.getId(), ex);
                }
            }
        }
    }
}
