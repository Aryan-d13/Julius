# Changelog

All notable changes to Julius will be documented in this file.

## [Unreleased] - 2026-07-11

### Fixed
*   **Billing Transactional & Concurrency Hardening (Red-Team Audit):**
    *   Resolved `@Transactional` self-invocation in `OutboxProcessor` by extracting event execution logic into a dedicated `OutboxEventService` bean, securing Spring AOP proxy boundary enforcement.
    *   Introduced `SELECT FOR UPDATE SKIP LOCKED` pessimistic locking for pending outbox events polling, eliminating multi-instance race conditions and status overwrite conflicts.
    *   Enforced explicit `@Transactional` request-scoped boundaries on `submitJob` and `dispatchRender` controller endpoints, guaranteeing atomic Compare-And-Swap (CAS) quota rollback on downstream execution failures.
    *   Secured `BillingController` checkout and portal endpoints against privilege escalation by injecting `PermissionEvaluator` and validating `billing.manage` organizational permissions.

### Added
*   **Billing, Quotas & Subscription Platform (Epic 12):**
    *   Implemented a production-ready billing engine integrated with Stripe Checkout sessions and Stripe Customer Portal.
    *   Created a true balanced Double-Entry Accounting Ledger structure (`Journal`, `Account`, `Transaction`, `JournalEntry`) ensuring debit/credit parity.
    *   Designed a Compare-And-Swap (CAS) optimized `QuotaEngine` using atomic update locks to prevent race-condition concurrency bypasses.
    *   Built a secure Stripe Webhook listener supporting idempotency keys signature validation and event replay protection.
    *   Configured a Transactional Outbox processor executing asynchronous downstream events securely.
    *   Added administrative operational REST endpoints for billing management, manual credits, and ledgers auditing.
*   **Interactive Editing & Subtitle Platform (Epic 9):** Built non-destructive versioned editing session domain layers (`EditSession`, `ClipVersion`, `SubtitleStyle`, `RenderProfile`, `RenderArtifact`).
*   **WAV Audio Waveform Peak Ingest:** Implemented `WaveformGenerator` extracting and caching normalized amplitude arrays during media ingestion.
*   **FFmpeg Thumbnail Sprite Tiling:** Created `SpriteGenerator` producing sprite sheet image blocks and metadata grids for editor scrubber timelines.
*   **Font Registry Management:** Introduced `FontRegistry` dynamically downloading and caching whitelisted TrueType Fonts (TTF).
*   **Advanced SubStation Alpha (ASS) Subtitle Compiler:** Implemented `SubtitleCompiler` generating karaoke subtitle scripts for FFmpeg filter overlays.
*   **Timeline Integrity Constraints Engine:** Implemented `TimelineEngine` validating duration boundaries, overlaps, and asset references.
*   **Figma-like Undo/Redo Client Engine:** Integrated command state stacks and keyboard triggers.
*   **Frontend Modularization & Feature-Slicing (Epic 8.5):** Extracted the legacy single-file dashboard component `app/page.tsx` into a proper Next.js App Router sub-routes group hierarchy (`(auth)`, `(dashboard)`, etc.).
*   **Business Feature Folders:** Introduced a `features/` directory organizing auth, jobs, clips, and admin components.
*   **UI Primitives Library:** Created a reusable `components/ui/` primitive wrapper system (`Button`, `Input`, `Card`).
*   **Domain Service Layers:** Distributed the global `apiClient` into isolated service endpoints communicating through a centralized `httpClient` base fetch client.
*   **Next.js Error Boundaries:** Added route files (`loading.tsx`, `error.tsx`, `global-error.tsx`, `not-found.tsx`) mapping loader shimmers and crash boundaries.
*   **Julius Customer Platform Web Application (Epic 8):** Implemented the Next.js production-ready customer dashboard styled with custom Vanilla CSS variables theme.
*   **Integrated Multi-View Client Routing:** Orchestrates pages for Landing, Login/Register Authentication, Wizard Onboardings, Active Jobs Details, Clip Library, and Control Settings.
*   **Interactive Split-Screen Clip Viewer:** Embedded video previews, clickable word-seeking transcripts, caption hooks presets, and download anchors.
*   **Keyboard Shortcut Bindings:** Implemented listeners for play/pause (`Space`), seeks (`J`/`K`), and palette trigger (`Cmd+K`).
*   **Real-time EventSource Monitor:** Binds log terminal scrolls and progress timelines to worker SSE channels.
*   **Enterprise Operations & Admin Platform (Epic 7):** Designed and implemented the complete admin REST platform under `/api/admin/**` path mappings.
*   **Polymorphic Global Search:** Added centralized `/search` endpoint resolving query hits across users, organizations, workspaces, and jobs.
*   **Operator Internal Notes:** Added database support for attaching operator-only debugging notes to users, organizations, and workspaces.
*   **Chronological Activity Timelines:** Traverses user registration, org joining, and login logs.
*   **Telemetry Aggregations:** Exposes real-time Whisper/Gemini token cost counts and worker thread utilization benchmarks.
*   **Action Interventions:** Supports remote job retry triggers and cancellations.
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
