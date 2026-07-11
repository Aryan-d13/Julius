-- =============================================================================
-- V5__admin_audit_schema.sql
-- Julius — Enterprise Operations & Admin Platform
-- =============================================================================

-- Seed Admin roles
INSERT INTO roles (id, name) VALUES ('role-op-super-admin-9999', 'ROLE_OPERATOR_SUPER_ADMIN');
INSERT INTO roles (id, name) VALUES ('role-op-developer-8888', 'ROLE_OPERATOR_DEVELOPER');
INSERT INTO roles (id, name) VALUES ('role-op-support-7777', 'ROLE_OPERATOR_SUPPORT');
INSERT INTO roles (id, name) VALUES ('role-op-auditor-6666', 'ROLE_OPERATOR_AUDITOR');

-- Seed Admin role permissions
INSERT INTO role_permissions (role_id, permission) VALUES ('role-op-super-admin-9999', 'admin.all');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-op-developer-8888', 'admin.jobs.retry');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-op-developer-8888', 'admin.flags.manage');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-op-support-7777', 'admin.users.read');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-op-support-7777', 'admin.users.sessions.revoke');

-- ─── admin_audit_events ──────────────────────────────────────────────────────
CREATE TABLE admin_audit_events (
    id                  VARCHAR(36)     NOT NULL,
    operator_user_id    VARCHAR(36)     NOT NULL,
    action              VARCHAR(100)    NOT NULL, -- 'SESSION_REVOKE', 'IMPERSONATION'
    target_resource_id  VARCHAR(255),
    ip_address          VARCHAR(45)     NOT NULL,
    user_agent          VARCHAR(500),
    details             TEXT            NOT NULL, -- JSON formatted details
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_admin_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_operator FOREIGN KEY (operator_user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_admin_audit_operator ON admin_audit_events (operator_user_id);
CREATE INDEX idx_admin_audit_created_at ON admin_audit_events (created_at);

-- ─── internal_notes ──────────────────────────────────────────────────────────
CREATE TABLE internal_notes (
    id                  VARCHAR(36)     NOT NULL,
    entity_type         VARCHAR(50)     NOT NULL, -- 'USER', 'ORGANIZATION', 'WORKSPACE'
    entity_id           VARCHAR(36)     NOT NULL,
    operator_user_id    VARCHAR(36)     NOT NULL,
    note_text           TEXT            NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_internal_notes PRIMARY KEY (id),
    CONSTRAINT fk_internal_notes_operator FOREIGN KEY (operator_user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_internal_notes_lookup ON internal_notes (entity_type, entity_id);
