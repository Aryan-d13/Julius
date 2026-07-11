package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "clip_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClipVersion {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private EditSession session;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "name")
    private String name;

    @Column(name = "timeline_state_json", nullable = false, columnDefinition = "text")
    private String timelineStateJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "style_preset_id", nullable = false)
    private SubtitleStyle stylePreset;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }
}
