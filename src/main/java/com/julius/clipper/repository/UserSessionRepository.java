package com.julius.clipper.repository;

import com.julius.clipper.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    Optional<UserSession> findByTokenHash(String tokenHash);
    Optional<UserSession> findByTokenHashOrPreviousTokenHash(String tokenHash, String previousTokenHash);
    List<UserSession> findByUserIdAndRevokedFalse(String userId);
}
