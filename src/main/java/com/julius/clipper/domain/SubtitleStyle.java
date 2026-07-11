package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subtitle_styles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubtitleStyle {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "font_name", nullable = false, length = 100)
    private String fontName;

    @Column(name = "font_size", nullable = false)
    private int fontSize;

    @Column(name = "primary_color", nullable = false, length = 10)
    private String primaryColor;

    @Column(name = "secondary_color", nullable = false, length = 10)
    private String secondaryColor;

    @Column(name = "outline_color", nullable = false, length = 10)
    private String outlineColor;

    @Column(name = "shadow_color", nullable = false, length = 10)
    private String shadowColor;

    @Column(name = "outline_width", nullable = false)
    private double outlineWidth;

    @Column(name = "shadow_depth", nullable = false)
    private double shadowDepth;

    @Column(name = "alignment", nullable = false)
    private int alignment;

    @Column(name = "safe_zone_vertical", nullable = false)
    private int safeZoneVertical;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
    }
}
