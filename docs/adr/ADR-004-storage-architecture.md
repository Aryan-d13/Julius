# ADR-004: Cloud-Native Storage Architecture & Trade-Offs

## Status
Proposed (Epic 2 Design Approval)

## Context
The initial implementation of Julius relied on direct absolute filesystem paths (such as `E:/Code/...` or `/tmp/...`) to pass media and metadata references between pipeline workers. While functional for a single local development VM, this design is a blocker for scaling to a cloud-native SaaS model (e.g. running multiple concurrent worker tasks on stateless Google Cloud Run containers). 

To scale the render pipeline, we need to extract file state management from the worker processors and introduce a unified, object-based storage interface.

## Decision
We will introduce a provider-agnostic `StorageClient` interface that treats the storage layer as an object store rather than a filesystem:

1.  **Object Storage Abstraction:** Expose methods using `InputStream` and `OutputStream` abstractions. The application code will not know if the backend is Local, GCS, S3, or MinIO.
2.  **Metadata Snapshot Model:** Rather than returning raw strings, all uploads and metadata checks will return a `StoredObject` record carrying metadata (size, content type, MD5 checksum, public/signed URI, etc.).
3.  **Strict Namespace Key Conventions:** All keys must be constructed programmatically through a centralized `StorageKeyBuilder` rather than arbitrary string concatenation.
4.  **Local Simulation (`LocalStorageClient`):** For fast local developer setups and lightweight unit testing, a filesystem-backed local simulator will be used instead of forcing developers/CI pipelines to run a Docker-based GCS emulator.

## Key Conventions & Namespace

All files must follow the namespaces managed by `StorageKeyBuilder`:
*   `raw/audio_{clipId}.wav` - Audio track source tracks.
*   `raw/video_{clipId}.mp4` - Video source tracks.
*   `jobs/{jobId}/clips/clip_{index}_{templateRef}.mp4` - Final cut output segments.
*   `jobs/{jobId}/transcripts/transcript_{clipId}.json` - Intermediate Whisper transcripts.
*   `cache/analysis_{clipId}.json` - Engagement metrics data.
*   `thumbnails/clip_{index}.jpg` - Clip screenshots.

## Reliability & Security
*   **Timeouts and Retries:** Network operations are wrapped in retry loops supporting exponential backoff for transient API errors.
*   **Checksum Validation:** Uploads and downloads will calculate MD5 hashes on the fly and verify matches to guarantee transfer integrity.
*   **Signed URLs:** Expose signed download URLs with a temporary lifespan (default 1 hour) instead of exposing public storage buckets.

## Consequences
*   **Pros:** Worker tasks can now run on isolated, horizontal containers. Scale-out is limited only by database locking and task queuing bounds rather than server storage size.
*   **Cons:** Minor increase in code complexity due to stream piping.
