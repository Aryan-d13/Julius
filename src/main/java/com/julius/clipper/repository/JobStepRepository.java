package com.julius.clipper.repository;

import com.julius.clipper.domain.JobStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobStepRepository extends JpaRepository<JobStep, String> {
    List<JobStep> findByJobId(String jobId);
    Optional<JobStep> findByJobIdAndStepType(String jobId, String stepType);
}
