package com.julius.clipper.repository;

import com.julius.clipper.domain.SubscriptionStateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubscriptionStateHistoryRepository extends JpaRepository<SubscriptionStateHistory, String> {
    List<SubscriptionStateHistory> findBySubscriptionIdOrderByCreatedAtDesc(String subscriptionId);
}
