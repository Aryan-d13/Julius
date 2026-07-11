# ADR 013: Interactive Editing & Subtitle Platform Architecture

## Status
Accepted

## Context
Evolving Julius from a simple automated clip rendering system into a professional-grade AI video editor requires decoupled draft hierarchies, separate preview pipelines, and a highly customizable template renderer.

## Decision
We implement a versioned, command-based, non-destructive editing platform for Julius.

### 1. Immature/Immutable AI Base Decoupling
AI clip generation states are kept immutable. We map edits through:
`Job` -> `GeneratedClip` -> `EditSession` -> `ClipVersion` -> `RenderArtifact`.

### 2. Dual Pipeline Architecture
*   **Editor Preview:** Next.js reactive player overlays synchronizing styled text changes locally in the browser DOM (0ms rendering lag).
*   **Video Renderer:** Decoupled `Renderer` transcoders compile timeline states into Advanced SubStation Alpha (`.ass`) formatting burned onto clips using FFmpeg filters on final export queues.

### 3. Command Stack Undo/Redo Engine
User intent maps through serializable command history stacks, avoiding full timeline JSON duplication:
`TrimSegmentCommand`, `EditWordTextCommand`, `ChangeSubtitleStyleCommand`.

### 4. Idempotency Hashing & Redis Locks
 Simultaneous export requests are filtered using deterministic SHA-256 hashes (`versionId`, `profileId`, `styleId`, `timelineState`). Duplicate actions block on Redis-based distributed locks (`lock:render:<hash>`), subscribing to active tasks instead of wasting rendering threads.

## Consequences
*   Sub-second preview response times.
*   Zero duplicate transcoding tasks under concurrent team export actions.
*   Clean plugin boundaries allowing GPU cloud node replacements without editor rewrites.
