package com.julius.clipper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.OutboxEvent;
import com.julius.clipper.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class OutboxEventService {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    public OutboxEventService(
            OutboxEventRepository outboxEventRepository,
            BillingService billingService,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.billingService = billingService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleEvent(OutboxEvent event) throws Exception {
        log.info("Processing outbox event: id={}, type={}", event.getId(), event.getEventType());
        
        Map<?, ?> payload = objectMapper.readValue(event.getPayload(), Map.class);
        String orgId = (String) payload.get("organizationId");
        
        if ("USAGE_DEBIT".equalsIgnoreCase(event.getEventType())) {
            Number quantityNum = (Number) payload.get("quantity");
            double quantity = quantityNum != null ? quantityNum.doubleValue() : 0.0;
            // Convert to minor unit cents (e.g. $0.10/min)
            long amountMinor = (long) (quantity * 10); // 10 cents per unit
            if (amountMinor > 0) {
                billingService.recordUsageDebitLedger(orgId, amountMinor, event.getCorrelationId());
            }
        } else if ("PROMOTIONAL_CREDIT".equalsIgnoreCase(event.getEventType())) {
            Number amountNum = (Number) payload.get("amount");
            long amount = amountNum != null ? amountNum.longValue() : 0L;
            if (amount > 0) {
                billingService.recordPromotionalCreditLedger(orgId, amount, event.getCorrelationId());
            }
        } else {
            log.warn("Unknown outbox event type: {}", event.getEventType());
        }

        event.setStatus("PROCESSED");
        event.setProcessedAt(LocalDateTime.now());
        outboxEventRepository.save(event);
        log.info("Successfully processed outbox event: {}", event.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventFailed(OutboxEvent event) {
        event.setStatus("FAILED");
        outboxEventRepository.save(event);
    }
}
