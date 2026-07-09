# Changelog

All notable changes to Julius will be documented in this file.

## [Unreleased] - 2026-07-09

### Added
*   **Provider-based Feature Flag System (Phase 4):** Introduced a decoupled, extensible feature toggling mechanism (`FeatureFlagService`) powered by a chain of `FeatureFlagProvider` implementations.
*   **Typed Map Feature Flags Configuration:** Added `FeatureFlagsProperties` configuration record binding `clipper.features.*` to a typed `Map<String, Boolean>` to support dynamic, compile-free runtime feature toggles.
*   **Local Property Feature Toggle Provider:** Created `LocalPropertyFeatureFlagProvider` with `@Order(100)` to resolve local flag configurations with high precedence.

### Changed (Breaking Changes)
*   **Configuration Keys Namespace Overhaul (Pre-1.0 Breaking Change):** Migrated configuration properties from legacy key layouts to a centralized, namespace-based schema (`clipper.workspace`, `clipper.download`, `clipper.ai`, `clipper.queue`, `clipper.storage`, `clipper.telemetry`, `clipper.security`, `clipper.worker`).
*   Removed all raw `@Value` injections across the codebase. Constructor injection is now strictly enforced with `@ConfigurationProperties` record classes.
*   Decoupled local workspace directories (`clipper.workspace.*`) from GCS/Local storage configurations (`clipper.storage.*`).
