package com.julius.clipper.repository;

import com.julius.clipper.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, String> {
    
    @Query("SELECT m FROM Membership m JOIN FETCH m.role WHERE m.user.id = :userId AND m.organization.id = :organizationId AND m.status = 'ACTIVE' AND m.deletedAt IS NULL")
    Optional<Membership> findActiveMembership(@Param("userId") String userId, @Param("organizationId") String organizationId);

    @Query("SELECT m FROM Membership m JOIN FETCH m.role WHERE m.user.id = :userId AND m.status = 'ACTIVE' AND m.deletedAt IS NULL")
    List<Membership> findActiveMembershipsForUser(@Param("userId") String userId);

    @Query("SELECT m FROM Membership m WHERE m.organization.id = :organizationId AND m.deletedAt IS NULL")
    List<Membership> findByOrganizationIdAndDeletedAtIsNull(@Param("organizationId") String organizationId);
}
