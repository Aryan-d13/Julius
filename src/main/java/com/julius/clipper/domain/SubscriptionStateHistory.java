package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_state_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStateHistory {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "subscription_id", nullable = false, length = 36)
    private String subscriptionId;

    @Column(name = "from_state", length = 50)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 50)
    private String toState;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "initiator", nullable = false, length = 100)
    private String initiator; // 'STRIPE_WEBHOOK', 'ADMIN', 'SYSTEM'

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON payload

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
