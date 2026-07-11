package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "render_artifacts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderArtifact {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private ClipVersion version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private RenderProfile profile;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // 'PENDING', 'QUEUED', 'PREPARING', 'RENDERING', 'UPLOADING', 'COMPLETED', 'FAILED'

    @Column(name = "render_hash", nullable = false, length = 64)
    private String renderHash;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "url")
    private String url;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }
}
