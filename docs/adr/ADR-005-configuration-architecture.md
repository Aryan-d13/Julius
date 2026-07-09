# ADR 005: Configuration, Secrets & Environment Architecture

## Status
Accepted

## Context
In early iterations, configuration settings in Julius were scattered across the codebase using Spring's `@Value` annotation. This pattern introduced several maintenance challenges:
1.  **Implicit Defaults**: Defaults were duplicated between Java annotations and properties files.
2.  **No Type Safety**: Injected properties were treated as raw primitives or strings, without central structural validation.
3.  **High Coupling**: Local working directories (FFmpeg, yt-dlp scratch paths) were mixed with cloud storage credentials and provider-level definitions.

## Decision
We establish a strongly typed, centralized, and environment-aware configuration architecture. 

### 1. Grouping Principles & Configuration Classes
*   Configuration settings must be grouped logically by domain and mapped to immutable record classes annotated with `@ConfigurationProperties`.
*   A new configuration class should be introduced when a new domain boundary or distinct third-party integration is added (e.g. `DownloadProperties` for yt-dlp parameters).
*   No defaults should be hardcoded in Java records; instead, defaults must reside strictly in the configuration profiles (`application.properties` / `application-live.properties`).

### 2. Separation of Workspace & Storage Concerns
*   **`StorageProperties`**: Configures the object storage backend (e.g., GCS vs Local root directories).
*   **`WorkspaceProperties`**: Configures the local directory paths used as scrap space by pipeline workers.
*   *Rationale*: A cloud container running in production writes objects to a GCS bucket (`StorageProperties`) but still requires a transient local disk scratchpad to run command-line tools like FFmpeg or yt-dlp (`WorkspaceProperties`). Keeping them separate decouples the file access model from the execution context.

### 3. Preference for `@ConfigurationProperties` over `@Value`
*   **Fail-Fast Bean Validation**: Supports JSR-380 validation on startup before any application logic or background thread runs.
*   **Metadata Autocompletion**: Automatically registers property metadata for IDE autocompletion.
*   **Centralized Logging & Fingerprinting**: Permits masking credentials (e.g., replacement with `<secret>` placeholders) and computing deterministic config hashes.

## Consequences
*   All existing raw `@Value` usages are eliminated.
*   Application startup fails immediately with `ConfigurationValidationException` if active configuration parameters violate defined constraints.
