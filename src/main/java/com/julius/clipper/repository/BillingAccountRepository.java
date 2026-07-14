package com.julius.clipper.repository;

import com.julius.clipper.domain.BillingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingAccountRepository extends JpaRepository<BillingAccount, String> {
    Optional<BillingAccount> findByJournalIdAndName(String journalId, String name);
    List<BillingAccount> findByJournalId(String journalId);
}
