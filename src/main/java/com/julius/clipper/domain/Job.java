package com.julius.clipper.domain;

import com.julius.clipper.domain.dto.JobConfig;
import com.julius.clipper.domain.converter.JobConfigConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs", indexes = {
    @Index(name = "idx_jobs_user_id", columnList = "user_id"),
    @Index(name = "idx_jobs_correlation_id", columnList = "correlation_id"),
    @Index(name = "idx_jobs_status", columnList = "status"),
    @Index(name = "idx_jobs_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private JobDBStatus status = JobDBStatus.PENDING;

    @Convert(converter = JobConfigConverter.class)
    @Column(name = "config", nullable = false, columnDefinition = "text")
    @Builder.Default
    private JobConfig config = new JobConfig();

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "clips_ready", nullable = false)
    @Builder.Default
    private int clipsReady = 0;

    @Column(name = "clip_count", nullable = false)
    @Builder.Default
    private int clipCount = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "fork_entered_at")
    private LocalDateTime forkEnteredAt;

    @Column(name = "join_satisfied_at")
    private LocalDateTime joinSatisfiedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public JobDBStatus deriveStatus() {
        if (status == JobDBStatus.CANCELLED) {
            return JobDBStatus.CANCELLED;
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            return JobDBStatus.FAILED;
        }
        if (completedAt != null) {
            return JobDBStatus.COMPLETED;
        }
        if (startedAt != null) {
            return JobDBStatus.PROCESSING;
        }
        return JobDBStatus.PENDING;
    }

    public String derivePhase() {
        if (status == JobDBStatus.CANCELLED) {
            return "cancelled";
        }
        if (status == JobDBStatus.FAILED || (errorMessage != null && !errorMessage.isBlank())) {
            return "failed";
        }
        if (status == JobDBStatus.COMPLETED || completedAt != null) {
            return "completed";
        }
        if (joinSatisfiedAt != null) {
            return "rendering";
        }
        if (forkEnteredAt != null) {
            return "forked";
        }
        if (startedAt != null) {
            return "downloading";
        }
        return "queued";
    }

    public String toApiStatus() {
        return status != null ? status.name().toLowerCase() : "pending";
    }
}
