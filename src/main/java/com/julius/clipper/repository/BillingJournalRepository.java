package com.julius.clipper.repository;

import com.julius.clipper.domain.BillingJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BillingJournalRepository extends JpaRepository<BillingJournal, String> {
    Optional<BillingJournal> findByOrganizationId(String organizationId);
}
