package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "internal_notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalNote {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // 'USER', 'ORGANIZATION', 'WORKSPACE'

    @Column(name = "entity_id", nullable = false, length = 36)
    private String entityId;

    @Column(name = "operator_user_id", nullable = false, length = 36)
    private String operatorUserId;

    @Column(name = "note_text", nullable = false, columnDefinition = "text")
    private String noteText;

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
