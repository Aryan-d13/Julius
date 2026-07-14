package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quota_usage_snapshots")
@IdClass(QuotaUsageSnapshotId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaUsageSnapshot {
    @Id
    @Column(name = "organization_id", length = 36)
    private String organizationId;

    @Id
    @Column(name = "feature_id", length = 100)
    private String featureId; // 'MINUTES_PROCESSED', 'AI_TOKENS', 'STORAGE_BYTES', 'RENDER_JOBS', 'SEATS'

    @Column(name = "current_usage", nullable = false)
    @Builder.Default
    private double currentUsage = 0.0;

    @Column(name = "limit_value", nullable = false)
    @Builder.Default
    private double limitValue = 0.0;

    @Column(name = "is_unlimited", nullable = false)
    @Builder.Default
    private boolean isUnlimited = false;

    @Column(name = "last_updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastUpdatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = LocalDateTime.now();
    }
}
