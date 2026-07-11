package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "render_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderProfile {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "fps", nullable = false)
    private int fps;

    @Column(name = "video_bitrate_kbps", nullable = false)
    private int videoBitrateKbps;

    @Column(name = "audio_bitrate_kbps", nullable = false)
    private int audioBitrateKbps;

    @Column(name = "crop_strategy", nullable = false, length = 50)
    private String cropStrategy;

    @Column(name = "watermark_key")
    private String watermarkKey;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
    }
}
