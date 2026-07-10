package com.julius.clipper.repository;

import com.julius.clipper.domain.LoginAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginAuditLogRepository extends JpaRepository<LoginAuditLog, String> {
}
