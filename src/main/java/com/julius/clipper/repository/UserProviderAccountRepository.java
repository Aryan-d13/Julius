package com.julius.clipper.repository;

import com.julius.clipper.domain.UserProviderAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserProviderAccountRepository extends JpaRepository<UserProviderAccount, String> {
    Optional<UserProviderAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
