package com.julius.clipper.repository;

import com.julius.clipper.domain.Job;
import com.julius.clipper.domain.JobDBStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, String> {
    Optional<Job> findByIdempotencyKey(String idempotencyKey);
    long countByUserIdAndStatusIn(String userId, Collection<JobDBStatus> statuses);
    long countByUserIdAndStatus(String userId, JobDBStatus status);
}
