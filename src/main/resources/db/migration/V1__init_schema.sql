-- =============================================================================
-- V1__init_schema.sql
-- Julius — Initial Database Schema
-- =============================================================================
-- Creates all 7 tables matching the existing JPA entity definitions.
-- Compatible with both PostgreSQL 16+ and H2 (MODE=PostgreSQL).
-- Verified against Hibernate validate mode.
-- =============================================================================

-- ─── users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              VARCHAR(36)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(255),
    created_at      TIMESTAMP       NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ─── jobs ────────────────────────────────────────────────────────────────────
CREATE TABLE jobs (
    id                  VARCHAR(36)     NOT NULL,
    user_id             VARCHAR(36),
    correlation_id      VARCHAR(255)    NOT NULL,
    status              VARCHAR(255)    NOT NULL,
    config              TEXT            NOT NULL,
    current_step        VARCHAR(255),
    clips_ready         INTEGER         NOT NULL,
    clip_count          INTEGER         NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    error_message       VARCHAR(500),
    error_code          VARCHAR(255),
    retry_count         INTEGER         NOT NULL,
    idempotency_key     VARCHAR(255),
    fork_entered_at     TIMESTAMP,
    join_satisfied_at   TIMESTAMP,
    CONSTRAINT pk_jobs PRIMARY KEY (id),
    CONSTRAINT uq_jobs_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_jobs_user_id        ON jobs (user_id);
CREATE INDEX idx_jobs_correlation_id ON jobs (correlation_id);
CREATE INDEX idx_jobs_status         ON jobs (status);
CREATE INDEX idx_jobs_created_at     ON jobs (created_at);

-- ─── tasks ───────────────────────────────────────────────────────────────────
CREATE TABLE tasks (
    id              VARCHAR(36)     NOT NULL,
    type            VARCHAR(255)    NOT NULL,
    payload         TEXT,
    status          VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL,
    started_at      TIMESTAMP,
    retries         INTEGER         NOT NULL,
    error           VARCHAR(1000),
    CONSTRAINT pk_tasks PRIMARY KEY (id)
);

-- ─── job_clips ───────────────────────────────────────────────────────────────
CREATE TABLE job_clips (
    id                  VARCHAR(36)         NOT NULL,
    job_id              VARCHAR(36)         NOT NULL,
    clip_index          INTEGER             NOT NULL,
    filename            VARCHAR(255)        NOT NULL,
    storage_key         VARCHAR(255),
    url                 VARCHAR(255),
    duration_seconds    DOUBLE PRECISION,
    size_bytes          BIGINT,
    created_at          TIMESTAMP           NOT NULL,
    CONSTRAINT pk_job_clips PRIMARY KEY (id),
    CONSTRAINT uq_job_clips_job_id_clip_index UNIQUE (job_id, clip_index),
    CONSTRAINT uq_job_clips_job_id_filename UNIQUE (job_id, filename)
);

-- ─── job_steps ───────────────────────────────────────────────────────────────
CREATE TABLE job_steps (
    id              VARCHAR(36)     NOT NULL,
    job_id          VARCHAR(36)     NOT NULL,
    step_type       VARCHAR(255)    NOT NULL,
    status          VARCHAR(255)    NOT NULL,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   VARCHAR(1000),
    step_metadata   TEXT,
    CONSTRAINT pk_job_steps PRIMARY KEY (id),
    CONSTRAINT uq_job_steps_job_id_step_type UNIQUE (job_id, step_type)
);

-- ─── clips ───────────────────────────────────────────────────────────────────
CREATE TABLE clips (
    id                  VARCHAR(36)     NOT NULL,
    source_url          VARCHAR(255),
    source_type         VARCHAR(255),
    storage_key         VARCHAR(255),
    status              VARCHAR(255),
    metadata_info       TEXT,
    analysis_results    TEXT,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_clips PRIMARY KEY (id)
);

-- ─── ux_facts ────────────────────────────────────────────────────────────────
CREATE TABLE ux_facts (
    id                  VARCHAR(36)     NOT NULL,
    slot                VARCHAR(255)    NOT NULL,
    language            VARCHAR(255)    NOT NULL,
    audience_scope      VARCHAR(255)    NOT NULL,
    headline            VARCHAR(255)    NOT NULL,
    body                VARCHAR(255)    NOT NULL,
    tag                 VARCHAR(255)    NOT NULL,
    canonical_hash      VARCHAR(255)    NOT NULL,
    near_dupe_hash      VARCHAR(255)    NOT NULL,
    source_model        VARCHAR(255),
    enabled             BOOLEAN         NOT NULL,
    used_count          INTEGER         NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_ux_facts PRIMARY KEY (id),
    CONSTRAINT uq_ux_facts_canonical_hash UNIQUE (canonical_hash)
);

CREATE INDEX idx_ux_facts_slot           ON ux_facts (slot);
CREATE INDEX idx_ux_facts_language       ON ux_facts (language);
CREATE INDEX idx_ux_facts_audience_scope ON ux_facts (audience_scope);
CREATE INDEX idx_ux_facts_enabled        ON ux_facts (enabled);
CREATE INDEX idx_ux_facts_created_at     ON ux_facts (created_at);
