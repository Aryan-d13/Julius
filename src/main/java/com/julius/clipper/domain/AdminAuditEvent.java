package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditEvent {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "operator_user_id", nullable = false, length = 36)
    private String operatorUserId;

    @Column(name = "action", nullable = false, length = 100)
    private String action; // 'SESSION_REVOKE', 'IMPERSONATION', 'NOTE_ATTACHED', 'JOB_RETRY'

    @Column(name = "target_resource_id", length = 255)
    private String targetResourceId;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "details", nullable = false, columnDefinition = "text")
    private String details; // JSON format details string

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
