package com.julius.clipper.repository;

import com.julius.clipper.domain.UsageEvent;
import com.julius.clipper.domain.UsageEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface UsageEventRepository extends JpaRepository<UsageEvent, UsageEventId> {
    
    @Query("SELECT COALESCE(SUM(u.quantity), 0.0) FROM UsageEvent u " +
           "WHERE u.organizationId = :orgId " +
           "  AND u.eventType = :eventType " +
           "  AND u.createdAt >= :start " +
           "  AND u.createdAt < :end")
    double sumQuantityByOrgAndTypeAndDateRange(
            @Param("orgId") String orgId, 
            @Param("eventType") String eventType, 
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end);
}
