package com.julius.clipper.repository;

import com.julius.clipper.domain.ClipVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ClipVersionRepository extends JpaRepository<ClipVersion, String> {
    @Query("SELECT cv FROM ClipVersion cv WHERE cv.session.id = :sessionId ORDER BY cv.versionNumber DESC LIMIT 1")
    Optional<ClipVersion> findLatestVersionForSession(@Param("sessionId") String sessionId);
}
