package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_events")
@IdClass(UsageEventId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageEvent {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType; // e.g. 'MINUTES_PROCESSED', 'AI_TOKENS'

    @Column(name = "quantity", nullable = false)
    private double quantity;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Id
    @Column(name = "created_at", nullable = false)
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
