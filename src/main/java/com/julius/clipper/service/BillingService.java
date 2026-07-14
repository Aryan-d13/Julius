package com.julius.clipper.service;

import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final BillingJournalRepository journalRepository;
    private final BillingAccountRepository accountRepository;
    private final BillingTransactionRepository transactionRepository;
    private final BillingJournalEntryRepository journalEntryRepository;
    private final QuotaUsageSnapshotRepository quotaRepository;
    private final UsageEventRepository usageEventRepository;

    public BillingService(
            BillingJournalRepository journalRepository,
            BillingAccountRepository accountRepository,
            BillingTransactionRepository transactionRepository,
            BillingJournalEntryRepository journalEntryRepository,
            QuotaUsageSnapshotRepository quotaRepository,
            UsageEventRepository usageEventRepository) {
        this.journalRepository = journalRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.quotaRepository = quotaRepository;
        this.usageEventRepository = usageEventRepository;
    }

    /**
     * Gets or creates a billing journal for the organization.
     */
    @Transactional
    public BillingJournal getOrCreateJournal(String orgId) {
        return journalRepository.findByOrganizationId(orgId)
                .orElseGet(() -> journalRepository.save(
                        BillingJournal.builder()
                                .organizationId(orgId)
                                .currency("USD")
                                .build()
                ));
    }

    /**
     * Gets or creates a ledger account of a specific type in a journal.
     */
    @Transactional
    public BillingAccount getOrCreateAccount(BillingJournal journal, String name, String type) {
        return accountRepository.findByJournalIdAndName(journal.getId(), name)
                .orElseGet(() -> accountRepository.save(
                        BillingAccount.builder()
                                .journal(journal)
                                .name(name)
                                .type(type)
                                .build()
                ));
    }

    /**
     * Records a balanced double-entry transaction.
     */
    @Transactional
    public BillingTransaction recordBalancedTransaction(
            String orgId,
            String correlationId,
            String description,
            List<EntryRequest> entries) {
        
        // Double-entry validation: sum of DEBITS must equal sum of CREDITS
        long sumDebits = 0;
        long sumCredits = 0;
        for (EntryRequest req : entries) {
            if ("DEBIT".equalsIgnoreCase(req.type)) {
                sumDebits += req.amountMinorUnits;
            } else if ("CREDIT".equalsIgnoreCase(req.type)) {
                sumCredits += req.amountMinorUnits;
            } else {
                throw new IllegalArgumentException("Invalid entry type: " + req.type);
            }
        }

        if (sumDebits != sumCredits) {
            throw new IllegalStateException("Double-entry journal is unbalanced! Debits (" 
                    + sumDebits + ") do not match Credits (" + sumCredits + ")");
        }

        // Fetch or create journal
        BillingJournal journal = getOrCreateJournal(orgId);

        // Deduplicate transaction using correlation_id
        BillingTransaction existing = transactionRepository.findByCorrelationId(correlationId).orElse(null);
        if (existing != null) {
            log.info("Transaction with correlationId {} already recorded. Skipping.", correlationId);
            return existing;
        }

        // Save transaction container
        BillingTransaction transaction = BillingTransaction.builder()
                .journal(journal)
                .correlationId(correlationId)
                .description(description)
                .build();
        transaction = transactionRepository.save(transaction);

        // Save entry items
        for (EntryRequest req : entries) {
            BillingAccount account = getOrCreateAccount(journal, req.accountName, req.accountType);
            BillingJournalEntry entry = BillingJournalEntry.builder()
                    .transaction(transaction)
                    .account(account)
                    .entryType(req.type.toUpperCase())
                    .amountMinorUnits(req.amountMinorUnits)
                    .build();
            journalEntryRepository.save(entry);
        }

        log.info("Successfully recorded balanced transaction for org={}: {}", orgId, description);
        return transaction;
    }

    /**
     * Records subscription payment (Invoice Paid).
     */
    @Transactional
    public void recordSubscriptionPaymentLedger(String orgId, long amountPaid, String currency, String invoiceId) {
        recordBalancedTransaction(
                orgId,
                invoiceId,
                "Stripe subscription invoice payment: " + invoiceId,
                List.of(
                        new EntryRequest("Cash (Stripe)", "ASSET", "DEBIT", amountPaid),
                        new EntryRequest("Subscription Revenue", "REVENUE", "CREDIT", amountPaid)
                )
        );
    }

    /**
     * Records usage debit ledger line.
     */
    @Transactional
    public void recordUsageDebitLedger(String orgId, long amount, String correlationId) {
        recordBalancedTransaction(
                orgId,
                correlationId,
                "Prepaid usage debit consumption: " + correlationId,
                List.of(
                        new EntryRequest("Prepaid Balance (Deferred Revenue)", "LIABILITY", "DEBIT", amount),
                        new EntryRequest("Prepaid Usage Revenue", "REVENUE", "CREDIT", amount)
                )
        );
    }

    /**
     * Records full/partial payment refunds.
     */
    @Transactional
    public void recordRefundLedger(String orgId, long amountRefunded, String correlationId) {
        recordBalancedTransaction(
                orgId,
                correlationId,
                "Refund processed: " + correlationId,
                List.of(
                        new EntryRequest("Subscription Revenue", "REVENUE", "DEBIT", amountRefunded),
                        new EntryRequest("Cash (Stripe)", "ASSET", "CREDIT", amountRefunded)
                )
        );
    }

    /**
     * Records disputes and dispute fees.
     */
    @Transactional
    public void recordChargebackLedger(String orgId, long amountChargedBack, long disputeFee, String correlationId) {
        recordBalancedTransaction(
                orgId,
                correlationId,
                "Chargeback processed: " + correlationId,
                List.of(
                        new EntryRequest("Subscription Revenue", "REVENUE", "DEBIT", amountChargedBack),
                        new EntryRequest("Dispute Fees Expense", "EXPENSE", "DEBIT", disputeFee),
                        new EntryRequest("Cash (Stripe)", "ASSET", "CREDIT", amountChargedBack + disputeFee)
                )
        );
    }

    /**
     * Records promotional credit allocation.
     */
    @Transactional
    public void recordPromotionalCreditLedger(String orgId, long amountPromotional, String correlationId) {
        recordBalancedTransaction(
                orgId,
                correlationId,
                "Promotional credit grant: " + correlationId,
                List.of(
                        new EntryRequest("Promotional Cost Expense", "EXPENSE", "DEBIT", amountPromotional),
                        new EntryRequest("Prepaid Balance (Deferred Revenue)", "LIABILITY", "CREDIT", amountPromotional)
                )
        );
    }

    /**
     * Records manual administrative waivers/adjustments.
     */
    @Transactional
    public void recordManualAdjustmentLedger(String orgId, long amountAdjusted, String correlationId) {
        recordBalancedTransaction(
                orgId,
                correlationId,
                "Manual adjustment debit waiver: " + correlationId,
                List.of(
                        new EntryRequest("Waivers & Adjustments Expense", "EXPENSE", "DEBIT", amountAdjusted),
                        new EntryRequest("Prepaid Balance (Deferred Revenue)", "LIABILITY", "CREDIT", amountAdjusted)
                )
        );
    }

    /**
     * Computes the current balance from all debit/credit entries for an asset or liability account.
     * Cash Balance = Debits - Credits
     * Liability Balance = Credits - Debits
     */
    @Transactional(readOnly = true)
    public long getAccountBalance(String orgId, String accountName) {
        BillingJournal journal = journalRepository.findByOrganizationId(orgId).orElse(null);
        if (journal == null) return 0;

        BillingAccount account = accountRepository.findByJournalIdAndName(journal.getId(), accountName).orElse(null);
        if (account == null) return 0;

        List<BillingJournalEntry> entries = journalEntryRepository.findByAccountId(account.getId());
        long totalDebit = 0;
        long totalCredit = 0;
        for (BillingJournalEntry entry : entries) {
            if ("DEBIT".equalsIgnoreCase(entry.getEntryType())) {
                totalDebit += entry.getAmountMinorUnits();
            } else if ("CREDIT".equalsIgnoreCase(entry.getEntryType())) {
                totalCredit += entry.getAmountMinorUnits();
            }
        }

        if ("ASSET".equalsIgnoreCase(account.getType()) || "EXPENSE".equalsIgnoreCase(account.getType())) {
            return totalDebit - totalCredit;
        } else {
            return totalCredit - totalDebit;
        }
    }

    /**
     * Atomically consumes quota using Compare-And-Swap (CAS).
     */
    @Transactional
    public void consumeQuota(String orgId, String featureId, double quantity) {
        // Find snapshot or create a default one
        quotaRepository.findByOrganizationIdAndFeatureId(orgId, featureId)
                .orElseGet(() -> quotaRepository.save(
                        QuotaUsageSnapshot.builder()
                                .organizationId(orgId)
                                .featureId(featureId)
                                .currentUsage(0.0)
                                .limitValue(10.0) // default fallback limit
                                .isUnlimited(false)
                                .build()
                ));

        int rowsUpdated = quotaRepository.consumeQuotaCas(orgId, featureId, quantity);
        if (rowsUpdated == 0) {
            throw new QuotaExceededException("Quota exceeded for feature " + featureId + " in organization " + orgId);
        }
        log.debug("Consumed {} of feature {} for org {}", quantity, featureId, orgId);
    }

    /**
     * Releases or reverts reserved quota.
     */
    @Transactional
    public void releaseQuota(String orgId, String featureId, double quantity) {
        quotaRepository.releaseQuotaCas(orgId, featureId, quantity);
        log.debug("Released {} of feature {} for org {}", quantity, featureId, orgId);
    }

    /**
     * Populates or updates quota snapshots based on plan tier entitlements.
     */
    @Transactional
    public void updateQuotaSnapshotsForPlan(String orgId, BillingPlan plan) {
        log.info("Updating quota snapshots for org {} to plan tier: {}", orgId, plan.getName());

        // Basic: 300 mins, 5GB storage, 50 renders
        // Pro: 1200 mins, 20GB storage, 200 renders
        // Enterprise: unlimited
        double minutesLimit = 300.0;
        double storageLimit = 5 * 1024 * 1024 * 1024.0; // 5 GB
        double rendersLimit = 50.0;
        boolean isUnlimited = false;

        if ("Professional".equalsIgnoreCase(plan.getName()) || plan.getName().contains("Pro")) {
            minutesLimit = 1200.0;
            storageLimit = 20 * 1024 * 1024 * 1024.0; // 20 GB
            rendersLimit = 200.0;
        } else if ("Enterprise".equalsIgnoreCase(plan.getName()) || plan.getName().contains("Enterprise")) {
            isUnlimited = true;
        }

        upsertSnapshot(orgId, "MINUTES_PROCESSED", minutesLimit, isUnlimited);
        upsertSnapshot(orgId, "STORAGE_BYTES", storageLimit, isUnlimited);
        upsertSnapshot(orgId, "RENDER_JOBS", rendersLimit, isUnlimited);
    }

    private void upsertSnapshot(String orgId, String featureId, double limitValue, boolean isUnlimited) {
        QuotaUsageSnapshot snapshot = quotaRepository.findByOrganizationIdAndFeatureId(orgId, featureId)
                .orElse(null);
        if (snapshot == null) {
            quotaRepository.save(
                    QuotaUsageSnapshot.builder()
                            .organizationId(orgId)
                            .featureId(featureId)
                            .currentUsage(0.0)
                            .limitValue(limitValue)
                            .isUnlimited(isUnlimited)
                            .build()
            );
        } else {
            snapshot.setLimitValue(limitValue);
            snapshot.setUnlimited(isUnlimited);
            quotaRepository.save(snapshot);
        }
    }

    public static class EntryRequest {
        public final String accountName;
        public final String accountType;
        public final String type;
        public final long amountMinorUnits;

        public EntryRequest(String accountName, String accountType, String type, long amountMinorUnits) {
            this.accountName = accountName;
            this.accountType = accountType;
            this.type = type;
            this.amountMinorUnits = amountMinorUnits;
        }
    }

    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}
