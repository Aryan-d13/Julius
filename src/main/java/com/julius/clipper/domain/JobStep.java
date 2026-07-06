package com.julius.clipper.domain;

import com.julius.clipper.domain.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "job_steps", uniqueConstraints = {
    @UniqueConstraint(name = "uq_job_steps_job_id_step_type", columnNames = {"job_id", "step_type"})
}, indexes = {
    @Index(name = "idx_job_steps_job_id", columnList = "job_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStep {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "job_id", length = 36, nullable = false)
    private String jobId;

    @Column(name = "step_type", nullable = false)
    private String stepType;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "step_metadata", columnDefinition = "text")
    @Builder.Default
    private Map<String, Object> stepMetadata = new HashMap<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
    }
}
