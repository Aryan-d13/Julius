-- =============================================================================
-- R__seed_dev_data.sql
-- Julius - Repeatable Development Seed Data
-- =============================================================================
-- This migration is re-executed whenever its checksum changes.
-- All inserts use WHERE NOT EXISTS for idempotency (ANSI SQL).
-- Compatible with both PostgreSQL and H2 (MODE=PostgreSQL).
-- =============================================================================

-- Development User
INSERT INTO users (id, email, full_name, created_at)
SELECT 'dev-user-0001', 'dev@julius.local', 'Julius Developer', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 'dev-user-0001');

-- Sample UX Facts
INSERT INTO ux_facts (id, slot, language, audience_scope, headline, body, tag, canonical_hash, near_dupe_hash, source_model, enabled, used_count, created_at)
SELECT
    'ux-fact-seed-001',
    'processing_wait',
    'en',
    'global',
    'AI processes 1 hour of video in under 3 minutes',
    'Our pipeline analyzes audio, transcribes speech, scores engagement, and renders clips automatically.',
    'Did you know?',
    'seed-hash-001',
    'seed-near-001',
    'seed',
    true,
    0,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ux_facts WHERE id = 'ux-fact-seed-001');

INSERT INTO ux_facts (id, slot, language, audience_scope, headline, body, tag, canonical_hash, near_dupe_hash, source_model, enabled, used_count, created_at)
SELECT
    'ux-fact-seed-002',
    'processing_wait',
    'en',
    'global',
    'Short-form clips get 3x more engagement',
    'Studies show clips between 15 and 60 seconds receive significantly higher watch-through rates.',
    'Did you know?',
    'seed-hash-002',
    'seed-near-002',
    'seed',
    true,
    0,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ux_facts WHERE id = 'ux-fact-seed-002');
