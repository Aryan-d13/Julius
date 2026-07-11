-- =============================================================================
-- V4__tenant_schema.sql
-- Julius — Authorization, Organizations & Multi-Tenancy Architecture
-- =============================================================================

-- ─── organizations ───────────────────────────────────────────────────────────
CREATE TABLE organizations (
    id              VARCHAR(36)     NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    personal        BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL,
    CONSTRAINT pk_organizations PRIMARY KEY (id)
);

-- ─── workspaces ──────────────────────────────────────────────────────────────
CREATE TABLE workspaces (
    id              VARCHAR(36)     NOT NULL,
    organization_id VARCHAR(36)     NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL,
    CONSTRAINT pk_workspaces PRIMARY KEY (id),
    CONSTRAINT fk_workspaces_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX idx_workspaces_org_id ON workspaces (organization_id);

-- Seed Org and Workspace Role definitions inside the roles table
INSERT INTO roles (id, name) VALUES ('role-org-owner-uuid-1111', 'ROLE_ORG_OWNER');
INSERT INTO roles (id, name) VALUES ('role-org-admin-uuid-2222', 'ROLE_ORG_ADMIN');
INSERT INTO roles (id, name) VALUES ('role-org-member-uuid-3333', 'ROLE_ORG_MEMBER');
INSERT INTO roles (id, name) VALUES ('role-ws-admin-uuid-4444', 'ROLE_WORKSPACE_ADMIN');
INSERT INTO roles (id, name) VALUES ('role-ws-member-uuid-5555', 'ROLE_WORKSPACE_MEMBER');
INSERT INTO roles (id, name) VALUES ('role-ws-viewer-uuid-6666', 'ROLE_WORKSPACE_VIEWER');

-- ─── role_permissions ────────────────────────────────────────────────────────
CREATE TABLE role_permissions (
    role_id         VARCHAR(36)     NOT NULL,
    permission      VARCHAR(100)    NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- Seed permissions
-- Org Owner permissions
INSERT INTO role_permissions (role_id, permission) VALUES ('role-org-owner-uuid-1111', 'org.invite');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-org-owner-uuid-1111', 'org.settings.edit');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-org-owner-uuid-1111', 'org.delete');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-org-owner-uuid-1111', 'billing.view');
-- Org Admin permissions
INSERT INTO role_permissions (role_id, permission) VALUES ('role-org-admin-uuid-2222', 'org.invite');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-org-admin-uuid-2222', 'org.settings.edit');
-- Workspace Admin permissions
INSERT INTO role_permissions (role_id, permission) VALUES ('role-ws-admin-uuid-4444', 'workspace.manage');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-ws-admin-uuid-4444', 'jobs.create');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-ws-admin-uuid-4444', 'jobs.delete');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-ws-admin-uuid-4444', 'jobs.share');
-- Workspace Member permissions
INSERT INTO role_permissions (role_id, permission) VALUES ('role-ws-member-uuid-5555', 'jobs.create');
INSERT INTO role_permissions (role_id, permission) VALUES ('role-ws-member-uuid-5555', 'jobs.share');

-- ─── memberships ─────────────────────────────────────────────────────────────
CREATE TABLE memberships (
    id              VARCHAR(36)     NOT NULL,
    user_id         VARCHAR(36)     NOT NULL,
    organization_id VARCHAR(36)     NOT NULL,
    role_id         VARCHAR(36)     NOT NULL,
    status          VARCHAR(50)     NOT NULL, -- 'ACTIVE', 'SUSPENDED'
    created_at      TIMESTAMP       NOT NULL,
    deleted_at      TIMESTAMP,
    CONSTRAINT pk_memberships PRIMARY KEY (id),
    CONSTRAINT uq_user_org UNIQUE (user_id, organization_id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE INDEX idx_memberships_user_id ON memberships (user_id);
CREATE INDEX idx_memberships_org_id ON memberships (organization_id);

-- ─── workspace_memberships ───────────────────────────────────────────────────
CREATE TABLE workspace_memberships (
    id              VARCHAR(36)     NOT NULL,
    workspace_id    VARCHAR(36)     NOT NULL,
    user_id         VARCHAR(36)     NOT NULL,
    role_id         VARCHAR(36)     NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    deleted_at      TIMESTAMP,
    CONSTRAINT pk_workspace_memberships PRIMARY KEY (id),
    CONSTRAINT uq_workspace_user UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_workspace_mem_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_mem_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_mem_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- ─── invitations ─────────────────────────────────────────────────────────────
CREATE TABLE invitations (
    id              VARCHAR(36)     NOT NULL,
    organization_id VARCHAR(36)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    role_id         VARCHAR(36)     NOT NULL,
    token_hash      VARCHAR(64)     NOT NULL,
    status          VARCHAR(50)     NOT NULL, -- 'PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED'
    expires_at      TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    CONSTRAINT pk_invitations PRIMARY KEY (id),
    CONSTRAINT uq_invitations_token UNIQUE (token_hash),
    CONSTRAINT fk_invitations_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- Link jobs to workspaces
ALTER TABLE jobs ADD COLUMN workspace_id VARCHAR(36);
ALTER TABLE jobs ADD COLUMN created_by_user_id VARCHAR(36);

CREATE INDEX idx_jobs_workspace_id ON jobs (workspace_id);
CREATE INDEX idx_jobs_creator_id ON jobs (created_by_user_id);
