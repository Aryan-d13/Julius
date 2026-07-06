package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_clips", uniqueConstraints = {
    @UniqueConstraint(name = "uq_job_clips_job_id_clip_index", columnNames = {"job_id", "clip_index"}),
    @UniqueConstraint(name = "uq_job_clips_job_id_filename", columnNames = {"job_id", "filename"})
}, indexes = {
    @Index(name = "idx_job_clips_job_id", columnList = "job_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobClip {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "job_id", length = 36, nullable = false)
    private String jobId;

    @Column(name = "clip_index", nullable = false)
    private int clipIndex;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "url")
    private String url;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "size_bytes")
    private Long sizeBytes;

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
