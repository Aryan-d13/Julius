-- =============================================================================
-- V7__billing_schema.sql
-- Julius — Billing, Quotas & Subscription Platform
-- =============================================================================

-- ─── 1. Billing Plans ────────────────────────────────────────────────────────
CREATE TABLE billing_plans (
    id                  VARCHAR(36)     NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    stripe_price_id     VARCHAR(100)    NOT NULL UNIQUE,
    amount_minor_units  BIGINT          NOT NULL,
    currency            VARCHAR(10)     NOT NULL DEFAULT 'USD',
    billing_interval    VARCHAR(30)     NOT NULL, -- 'MONTHLY', 'ANNUAL'
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_billing_plans PRIMARY KEY (id)
);

-- ─── 2. Subscriptions ────────────────────────────────────────────────────────
CREATE TABLE subscriptions (
    id                      VARCHAR(36)     NOT NULL,
    organization_id         VARCHAR(36)     NOT NULL UNIQUE,
    plan_id                 VARCHAR(36)     NOT NULL,
    stripe_subscription_id  VARCHAR(100)    UNIQUE,
    status                  VARCHAR(50)     NOT NULL, -- 'TRIALING', 'ACTIVE', 'PAST_DUE', 'DISPUTED', 'REFUNDED', 'SUSPENDED', 'CANCELED'
    version                 BIGINT          NOT NULL DEFAULT 0,
    current_period_start    TIMESTAMP       NOT NULL,
    current_period_end      TIMESTAMP       NOT NULL,
    canceled_at             TIMESTAMP,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP       NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES billing_plans(id)
);

CREATE INDEX idx_subscriptions_stripe ON subscriptions(stripe_subscription_id);

-- ─── 3. Subscription State History ───────────────────────────────────────────
CREATE TABLE subscription_state_history (
    id                  VARCHAR(36)     NOT NULL,
    subscription_id     VARCHAR(36)     NOT NULL,
    from_state          VARCHAR(50),
    to_state            VARCHAR(50)     NOT NULL,
    correlation_id      VARCHAR(100)    NOT NULL,
    initiator           VARCHAR(100)    NOT NULL, -- 'STRIPE_WEBHOOK', 'ADMIN', 'SYSTEM'
    metadata            TEXT,                    -- JSON string
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_sub_state_history PRIMARY KEY (id),
    CONSTRAINT fk_history_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE CASCADE
);

CREATE INDEX idx_sub_history_lookup ON subscription_state_history(subscription_id);

-- ─── 4. Double-Entry Accounting Ledger ───────────────────────────────────────
CREATE TABLE billing_journals (
    id                  VARCHAR(36)     NOT NULL,
    organization_id     VARCHAR(36)     NOT NULL UNIQUE,
    stripe_customer_id  VARCHAR(100)    UNIQUE,
    currency            VARCHAR(10)     NOT NULL DEFAULT 'USD',
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_billing_journals PRIMARY KEY (id),
    CONSTRAINT fk_billing_journals_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

CREATE TABLE billing_accounts (
    id                  VARCHAR(36)     NOT NULL,
    journal_id          VARCHAR(36)     NOT NULL,
    name                VARCHAR(100)    NOT NULL, -- e.g., 'Cash (Stripe)', 'Deferred Revenue', 'Subscription Revenue'
    type                VARCHAR(30)     NOT NULL, -- 'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_billing_accounts PRIMARY KEY (id),
    CONSTRAINT fk_billing_accounts_journal FOREIGN KEY (journal_id) REFERENCES billing_journals(id) ON DELETE CASCADE,
    CONSTRAINT uq_journal_account_name UNIQUE (journal_id, name)
);

CREATE TABLE billing_transactions (
    id                  VARCHAR(36)     NOT NULL,
    journal_id          VARCHAR(36)     NOT NULL,
    correlation_id      VARCHAR(100)    NOT NULL UNIQUE, -- Replay protection / Idempotency key
    description         VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_billing_transactions PRIMARY KEY (id),
    CONSTRAINT fk_billing_transactions_journal FOREIGN KEY (journal_id) REFERENCES billing_journals(id) ON DELETE CASCADE
);

CREATE TABLE billing_journal_entries (
    id                  VARCHAR(36)     NOT NULL,
    transaction_id      VARCHAR(36)     NOT NULL,
    account_id          VARCHAR(36)     NOT NULL,
    entry_type          VARCHAR(10)     NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount_minor_units  BIGINT          NOT NULL, -- Must be > 0
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_journal_entries PRIMARY KEY (id),
    CONSTRAINT fk_entries_transaction FOREIGN KEY (transaction_id) REFERENCES billing_transactions(id) ON DELETE CASCADE,
    CONSTRAINT fk_entries_account FOREIGN KEY (account_id) REFERENCES billing_accounts(id) ON DELETE CASCADE,
    CONSTRAINT chk_amount_positive CHECK (amount_minor_units > 0),
    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_journal_entries_account ON billing_journal_entries(account_id);

-- ─── 5. Quota Engine & Usage Snapshots ───────────────────────────────────────
CREATE TABLE quota_usage_snapshots (
    organization_id     VARCHAR(36)     NOT NULL,
    feature_id          VARCHAR(100)    NOT NULL, -- 'MINUTES_PROCESSED', 'AI_TOKENS', 'STORAGE_BYTES', 'RENDER_JOBS', 'SEATS'
    current_usage       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    limit_value         DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    is_unlimited        BOOLEAN         NOT NULL DEFAULT FALSE,
    last_updated_at     TIMESTAMP       NOT NULL,
    CONSTRAINT pk_quota_usage_snapshots PRIMARY KEY (organization_id, feature_id),
    CONSTRAINT fk_quota_snapshots_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

-- ─── 6. Usage Events (Partition-friendly key schema) ────────────────────────
CREATE TABLE usage_events (
    id                  VARCHAR(36)     NOT NULL,
    organization_id     VARCHAR(36)     NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,
    quantity            DOUBLE PRECISION NOT NULL,
    correlation_id      VARCHAR(100)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_usage_events PRIMARY KEY (id, created_at)
);

CREATE INDEX idx_usage_events_org ON usage_events(organization_id);

-- ─── 7. Outbox Events ────────────────────────────────────────────────────────
CREATE TABLE outbox_events (
    id                  VARCHAR(36)     NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,
    payload             TEXT            NOT NULL,
    status              VARCHAR(50)     NOT NULL, -- 'PENDING', 'PROCESSED', 'FAILED'
    correlation_id      VARCHAR(100)    NOT NULL UNIQUE,
    created_at          TIMESTAMP       NOT NULL,
    processed_at        TIMESTAMP,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_events_status ON outbox_events(status);

-- ─── 8. Webhook Idempotency Ledger ───────────────────────────────────────────
CREATE TABLE webhook_idempotency_ledger (
    event_id            VARCHAR(100)    NOT NULL,
    status              VARCHAR(50)     NOT NULL, -- 'RECEIVED', 'PROCESSED', 'FAILED'
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_webhook_idempotency PRIMARY KEY (event_id)
);

-- ─── 9. Add Permissions for Billing Management ────────────────────────────────
INSERT INTO role_permissions (role_id, permission) VALUES ('role-org-owner-uuid-1111', 'billing.manage');
