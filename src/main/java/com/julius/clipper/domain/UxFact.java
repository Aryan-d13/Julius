package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ux_facts", indexes = {
    @Index(name = "idx_ux_facts_slot", columnList = "slot"),
    @Index(name = "idx_ux_facts_language", columnList = "language"),
    @Index(name = "idx_ux_facts_audience_scope", columnList = "audience_scope"),
    @Index(name = "idx_ux_facts_enabled", columnList = "enabled"),
    @Index(name = "idx_ux_facts_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UxFact {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "slot", nullable = false)
    private String slot;

    @Column(name = "language", nullable = false)
    @Builder.Default
    private String language = "en";

    @Column(name = "audience_scope", nullable = false)
    @Builder.Default
    private String audienceScope = "global";

    @Column(name = "headline", nullable = false)
    private String headline;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "tag", nullable = false)
    @Builder.Default
    private String tag = "Did you know?";

    @Column(name = "canonical_hash", nullable = false, unique = true)
    private String canonicalHash;

    @Column(name = "near_dupe_hash", nullable = false)
    private String nearDupeHash;

    @Column(name = "source_model")
    private String sourceModel;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

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
