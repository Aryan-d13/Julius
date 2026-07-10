package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_metadata", nullable = false, columnDefinition = "jsonb")
    private String clientMetadata; // JSON string containing device info mapped as JSONB in PG

    @Column(name = "created_ip", nullable = false, length = 45)
    private String createdIp;

    @Column(name = "last_used_ip", nullable = false, length = 45)
    private String lastUsedIp;

    @Column(name = "rotation_counter", nullable = false)
    @Builder.Default
    private int rotationCounter = 0;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_used_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastUsedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastUsedAt == null) {
            lastUsedAt = LocalDateTime.now();
        }
    }
}
