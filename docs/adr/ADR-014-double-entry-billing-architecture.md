# ADR 014: Double-Entry Accounting Ledger and Quota Engine Architecture

## Status
Accepted

## Context
Julius requires a billing, subscription, and quota management platform that scales to millions of users. Standard database updates (e.g., storing simple counters in mutable rows) are highly vulnerable to concurrency race conditions and double-spending. Furthermore, enterprise SOC2 compliance and financial auditing require mathematical verification of balances and immutable history.

## Decision
We implement a true Double-Entry Accounting Ledger coupled with an optimistic Compare-And-Swap (CAS) Quota Engine.

### 1. True Double-Entry Ledger
*   **Journal**: Scope billing entries per organization.
*   **Account**: Define Asset, Liability, Equity, Revenue, and Expense accounts.
*   **Transaction**: Group balanced entries representing a financial event. Enforces replay protection via unique `correlation_id` keys.
*   **JournalEntry**: Balanced credit/debit records. Parity (`SUM(debits) = SUM(credits)`) is enforced at the transaction boundary.
*   **Immutability**: Ledger accounts and entries can never be modified or deleted. Balances are derived from historical logs.

### 2. Compare-And-Swap (CAS) Quota Engine
*   To prevent concurrency race conditions (e.g., executing multiple rendering exports at once to bypass limits), `quota_usage_snapshots` contains the current usage and limits.
*   Usage increments check and update balances via CAS SQL statements:
    `UPDATE quota_usage_snapshots SET current_usage = current_usage + :amount WHERE organization_id = :orgId AND feature_id = :featureId AND current_usage + :amount <= limit_value`
*   If the row count returned by the update is `0`, the request is denied due to insufficient quota.

### 3. Subscription Lifecycle State Machine
*   Subscriptions transition through deterministic states: `TRIALING`, `ACTIVE`, `PAST_DUE`, `DISPUTED`, `REFUNDED`, `SUSPENDED`, `CANCELED`.
*   All state transitions write audit rows to `subscription_state_history`.

### 4. Transactional Outbox Pattern
*   Instead of writing to external pub/sub channels within a business transaction (which could fail or cause distributed inconsistencies), the system writes outbound events to `outbox_events` within the database transaction.
*   A background Outbox Processor polls `outbox_events` and publishes events reliably to downstream subscribers.

### 5. Idempotent Webhook Processing
*   Stripe Webhooks are validated via SHA-256 signatures and processed idempotently using a `webhook_idempotency_ledger` table.

## Consequences
*   Mathematical verification of organization balances.
*   Zero double-spending or limit bypasses under high concurrency.
*   SOC2 / PCI auditable financial logs.
*   Fault-tolerant state machine tracking webhook updates.
