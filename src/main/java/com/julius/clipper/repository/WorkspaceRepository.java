package com.julius.clipper.repository;

import com.julius.clipper.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, String> {
    Optional<Workspace> findByIdAndDeletedAtIsNull(String id);
    List<Workspace> findByOrganizationIdAndDeletedAtIsNull(String organizationId);
}
