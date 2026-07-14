package com.julius.clipper.repository;

import com.julius.clipper.domain.QuotaUsageSnapshot;
import com.julius.clipper.domain.QuotaUsageSnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuotaUsageSnapshotRepository extends JpaRepository<QuotaUsageSnapshot, QuotaUsageSnapshotId> {
    
    Optional<QuotaUsageSnapshot> findByOrganizationIdAndFeatureId(String organizationId, String featureId);
    
    List<QuotaUsageSnapshot> findByOrganizationId(String organizationId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE quota_usage_snapshots " +
           "SET current_usage = current_usage + :increment, last_updated_at = CURRENT_TIMESTAMP " +
           "WHERE organization_id = :orgId " +
           "  AND feature_id = :featureId " +
           "  AND (is_unlimited = true OR (current_usage + :increment <= limit_value))", nativeQuery = true)
    int consumeQuotaCas(@Param("orgId") String orgId, 
                        @Param("featureId") String featureId, 
                        @Param("increment") double increment);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE quota_usage_snapshots " +
           "SET current_usage = current_usage - :decrement, last_updated_at = CURRENT_TIMESTAMP " +
           "WHERE organization_id = :orgId " +
           "  AND feature_id = :featureId", nativeQuery = true)
    int releaseQuotaCas(@Param("orgId") String orgId, 
                        @Param("featureId") String featureId, 
                        @Param("decrement") double decrement);
}
