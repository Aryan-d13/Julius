package com.julius.clipper.repository;

import com.julius.clipper.domain.JobClip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobClipRepository extends JpaRepository<JobClip, String> {
    List<JobClip> findByJobId(String jobId);
    long countByJobId(String jobId);
    Optional<JobClip> findByJobIdAndClipIndex(String jobId, int clipIndex);
    Optional<JobClip> findByJobIdAndFilename(String jobId, String filename);
}
