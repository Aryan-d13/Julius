# Changelog

All notable changes to Julius will be documented in this file.

## [Unreleased] - 2026-07-11

### Added
*   **Multi-Tenancy & Authorization Architecture (Epic 6):** Seeded organization, workspace, membership, and invitation relational mapping schemas with normalized roles and dynamic permissions.
*   **Request-Scoped ThreadLocal Cache:** Introduced `AuthorizationContext` lazy loading and caching resolved permission maps once per request, eliminating database query redundancy.
*   **Resource-Oriented Endpoint Architecture:** Scoped execution jobs directly under path parameters (`/api/workspaces/{workspaceId}/jobs`).
*   **Aspect-Oriented Method Security:** Configured custom `PermissionEvaluator` checking organizational and workspace boundaries using `@PreAuthorize`.
*   **Timestamp-based Soft Deletes:** Replaced primitive boolean flags with `deleted_at TIMESTAMP` columns for orgs and workspaces.
*   **Authentication & Session Architecture (Epic 5):** Added Spring Security Resource Server integration with CookieOrHeaderBearerTokenResolver interceptors.
*   **RTR Session Rotation:** Persist user sessions mapping client user-agents, IPs, and previous token hashes to revoke compromised session trees.
*   **Argon2id Hashing:** Password authentication defaults configured utilizing BouncyCastle providers.

### Changed (Breaking Changes)
*   Exposed execution jobs API endpoints strictly under `/api/workspaces/{workspaceId}/jobs`, enforcing explicit tenant context assertions.
*   **Configuration Keys Namespace Overhaul (Pre-1.0 Breaking Change):** Migrated configuration properties from legacy key layouts to a centralized, namespace-based schema (`clipper.workspace`, `clipper.download`, `clipper.ai`, `clipper.queue`, `clipper.storage`, `clipper.telemetry`, `clipper.security`, `clipper.worker`).
*   Removed all raw `@Value` injections across the codebase. Constructor injection is now strictly enforced with `@ConfigurationProperties` record classes.
*   Decoupled local workspace directories (`clipper.workspace.*`) from GCS/Local storage configurations (`clipper.storage.*`).
