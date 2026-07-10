-- =============================================================================
-- V3__auth_schema.sql
-- Julius — Authentication, Identity & Session Architecture Schema
-- Compatible with both PostgreSQL 16+ and H2 (PostgreSQL Mode)
-- =============================================================================

-- Add password hash column to users table
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

-- ─── roles ───────────────────────────────────────────────────────────────────
CREATE TABLE roles (
    id              VARCHAR(36)     NOT NULL,
    name            VARCHAR(50)     NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

-- Seed default roles
INSERT INTO roles (id, name) VALUES ('role-user-uuid-placeholder-1111', 'ROLE_USER');
INSERT INTO roles (id, name) VALUES ('role-admin-uuid-placeholder-2222', 'ROLE_ADMIN');

-- ─── user_roles ──────────────────────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id         VARCHAR(36)     NOT NULL,
    role_id         VARCHAR(36)     NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- ─── user_provider_accounts ──────────────────────────────────────────────────
CREATE TABLE user_provider_accounts (
    id                  VARCHAR(36)     NOT NULL,
    user_id             VARCHAR(36)     NOT NULL,
    provider            VARCHAR(50)     NOT NULL,
    provider_user_id    VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_user_provider_accounts PRIMARY KEY (id),
    CONSTRAINT uq_provider_account UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_provider_account_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ─── user_sessions ───────────────────────────────────────────────────────────
CREATE TABLE user_sessions (
    id                  VARCHAR(36)     NOT NULL,
    user_id             VARCHAR(36)     NOT NULL,
    token_hash          VARCHAR(64)     NOT NULL,
    previous_token_hash VARCHAR(64),
    client_metadata     JSONB           NOT NULL,
    created_ip          VARCHAR(45)     NOT NULL,
    last_used_ip        VARCHAR(45)     NOT NULL,
    rotation_counter    INTEGER         NOT NULL DEFAULT 0,
    revoked             BOOLEAN         NOT NULL DEFAULT FALSE,
    expires_at          TIMESTAMP       NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    last_used_at        TIMESTAMP       NOT NULL,
    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT uq_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_sessions_user_id ON user_sessions (user_id);
CREATE INDEX idx_sessions_token_hash ON user_sessions (token_hash);
CREATE INDEX idx_sessions_prev_token_hash ON user_sessions (previous_token_hash);

-- ─── login_audit_logs ────────────────────────────────────────────────────────
CREATE TABLE login_audit_logs (
    id                  VARCHAR(36)     NOT NULL,
    user_id             VARCHAR(36),
    session_id          VARCHAR(36),
    correlation_id      VARCHAR(255)    NOT NULL,
    request_id          VARCHAR(255)    NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,
    ip_address          VARCHAR(45)     NOT NULL,
    user_agent          VARCHAR(500),
    failure_reason      VARCHAR(255),
    processing_time_ms  BIGINT          NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_login_audit_logs PRIMARY KEY (id)
);

CREATE INDEX idx_login_audit_logs_user_id ON login_audit_logs (user_id);
CREATE INDEX idx_login_audit_logs_created_at ON login_audit_logs (created_at);
