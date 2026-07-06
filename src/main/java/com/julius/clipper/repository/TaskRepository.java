package com.julius.clipper.repository;

import com.julius.clipper.domain.Task;
import com.julius.clipper.pipeline.TaskStatus;
import com.julius.clipper.pipeline.TaskType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT t FROM Task t WHERE t.type = :type AND t.status = :status ORDER BY t.createdAt ASC")
    List<Task> findFirstForUpdateSkipLocked(
            @Param("type") TaskType type, 
            @Param("status") TaskStatus status, 
            Pageable pageable
    );
}
