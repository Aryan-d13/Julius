package com.julius.clipper.repository;

import com.julius.clipper.domain.WebhookIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookIdempotencyRepository extends JpaRepository<WebhookIdempotency, String> {
}
