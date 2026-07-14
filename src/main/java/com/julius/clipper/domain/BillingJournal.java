package com.julius.clipper.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "billing_journals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingJournal {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organization_id", nullable = false, unique = true, length = 36)
    private String organizationId;

    @Column(name = "stripe_customer_id", unique = true, length = 100)
    private String stripeCustomerId;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "USD";

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
