package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_idempotency_ledger")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookIdempotency {
    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // 'RECEIVED', 'PROCESSED', 'FAILED'

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
