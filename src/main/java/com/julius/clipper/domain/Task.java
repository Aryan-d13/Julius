package com.julius.clipper.domain;

import com.julius.clipper.domain.converter.MapJsonConverter;
import com.julius.clipper.pipeline.TaskStatus;
import com.julius.clipper.pipeline.TaskType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TaskType type;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "payload", columnDefinition = "text")
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "retries", nullable = false)
    @Builder.Default
    private int retries = 0;

    @Column(name = "error", length = 1000)
    private String error;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getUserId() {
        if (payload != null) {
            Object userId = payload.get("user_id");
            if (userId != null) {
                return userId.toString();
            }
        }
        return null;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getJobId() {
        if (payload != null) {
            Object jobId = payload.get("job_id");
            if (jobId != null) {
                return jobId.toString();
            }
        }
        return null;
    }
}
