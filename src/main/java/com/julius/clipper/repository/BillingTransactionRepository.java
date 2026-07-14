package com.julius.clipper.repository;

import com.julius.clipper.domain.BillingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, String> {
    Optional<BillingTransaction> findByCorrelationId(String correlationId);
}
