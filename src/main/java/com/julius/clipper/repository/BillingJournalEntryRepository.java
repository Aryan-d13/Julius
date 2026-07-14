package com.julius.clipper.repository;

import com.julius.clipper.domain.BillingJournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BillingJournalEntryRepository extends JpaRepository<BillingJournalEntry, String> {
    List<BillingJournalEntry> findByTransactionId(String transactionId);
    List<BillingJournalEntry> findByAccountId(String accountId);
}
