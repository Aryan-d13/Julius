package com.julius.clipper.repository;

import com.julius.clipper.domain.RenderArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RenderArtifactRepository extends JpaRepository<RenderArtifact, String> {
    Optional<RenderArtifact> findFirstByRenderHashAndStatusOrderByCreatedAtDesc(String renderHash, String status);
}
