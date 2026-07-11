package com.julius.clipper.repository;

import com.julius.clipper.domain.WorkspaceMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, String> {
    
    @Query("SELECT wm FROM WorkspaceMembership wm JOIN FETCH wm.role WHERE wm.user.id = :userId AND wm.workspace.id = :workspaceId AND wm.deletedAt IS NULL")
    Optional<WorkspaceMembership> findActiveMembership(@Param("userId") String userId, @Param("workspaceId") String workspaceId);

    List<WorkspaceMembership> findByWorkspaceIdAndDeletedAtIsNull(String workspaceId);
}
