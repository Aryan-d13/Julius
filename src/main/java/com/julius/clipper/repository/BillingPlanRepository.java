package com.julius.clipper.repository;

import com.julius.clipper.domain.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BillingPlanRepository extends JpaRepository<BillingPlan, String> {
    Optional<BillingPlan> findByStripePriceId(String stripePriceId);
}
