package com.julius.clipper.domain;

import com.julius.clipper.domain.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "clips")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clip {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "source_type")
    @Builder.Default
    private String sourceType = "other";

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "status")
    @Builder.Default
    private String status = "pending";

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "metadata_info", columnDefinition = "text")
    @Builder.Default
    private Map<String, Object> metadataInfo = new HashMap<>();

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "analysis_results", columnDefinition = "text")
    @Builder.Default
    private Map<String, Object> analysisResults = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

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
}
