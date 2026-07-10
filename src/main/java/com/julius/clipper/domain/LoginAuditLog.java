package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAuditLog {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "correlation_id", nullable = false, length = 255)
    private String correlationId;

    @Column(name = "request_id", nullable = false, length = 255)
    private String requestId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // 'LOGIN_SUCCESS', 'LOGIN_FAILED', 'REFRESH_SUCCESS', etc.

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "processing_time_ms", nullable = false)
    private long processingTimeMs;

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
