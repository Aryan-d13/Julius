package com.julius.clipper.repository;

import com.julius.clipper.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {
    Optional<Organization> findByIdAndDeletedAtIsNull(String id);
}
