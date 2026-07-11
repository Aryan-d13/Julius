package com.julius.clipper.repository;

import com.julius.clipper.domain.AdminAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, String> {
    List<AdminAuditEvent> findByTargetResourceIdOrderByCreatedAtDesc(String targetResourceId);
    List<AdminAuditEvent> findByOperatorUserIdOrderByCreatedAtDesc(String operatorUserId);
}
