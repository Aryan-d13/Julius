# THE RECONSTRUCTION MANIFEST — Julius AI Clipper

> **Purpose:** Absolute mental reconstruction. One monitor on this document, one on the IDE. By end of day, the entire system is wired into your head.
>
> **Codebase:** `com.julius.clipper` — Spring Boot 3.3 / Java 21
> **What it does:** Takes a YouTube URL, downloads the video, transcribes it with Whisper, sends the transcript to Google Gemini to identify viral moments, then renders short-form clips using FFmpeg.

---

## TABLE OF CONTENTS

1. [System Identity Card](#1-system-identity-card)
2. [End-to-End Execution Trace](#2-end-to-end-execution-trace)
3. [Pipeline DAG — The Real Picture](#3-pipeline-dag--the-real-picture)
4. [File-by-File Decomposition](#4-file-by-file-decomposition)
   - [4.1 Entry Point](#41-entry-point)
   - [4.2 Domain Layer](#42-domain-layer)
   - [4.3 Pipeline Infrastructure](#43-pipeline-infrastructure)
   - [4.4 Worker Implementations](#44-worker-implementations)
   - [4.5 Service Layer](#45-service-layer)
   - [4.6 Controller Layer](#46-controller-layer)
   - [4.7 Configuration](#47-configuration)
   - [4.8 Repositories](#48-repositories)
   - [4.9 Actuator & Health](#49-actuator--health)
   - [4.10 Frontend](#410-frontend)
   - [4.11 Python Bridge](#411-python-bridge)
   - [4.12 Tests](#412-tests)
   - [4.13 Build & Infrastructure](#413-build--infrastructure)
5. [System Orchestration & Component Boundaries](#5-system-orchestration--component-boundaries)
6. [Under-the-Hood Mechanics & Reality](#6-under-the-hood-mechanics--reality)

---

## 1. SYSTEM IDENTITY CARD

| Attribute | Value |
|---|---|
| **Language** | Java 21 + Python 3.x |
| **Framework** | Spring Boot 3.3.1 |
| **Build** | Maven (pom.xml) |
| **Database** | H2 in-memory (PostgreSQL compat mode) — no real Postgres yet |
| **Queue** | Redis Streams (live) / DB polling with `SELECT FOR UPDATE SKIP LOCKED` (test) |
| **AI — Transcription** | `faster-whisper` (large-v3-turbo model, CTranslate2 backend) via Python subprocess |
| **AI — Analysis** | Google Gemini API (`gemini-1.5-flash` / `gemini-3.5-flash`) |
| **Media Processing** | FFmpeg (audio extraction, video cutting, rendering), yt-dlp (YouTube download) |
| **Concurrency** | Java 21 Virtual Threads, Semaphore-based resource pools (IO=8, CPU=2, GPU=1) |
| **Event System** | Redis Streams + Redis Pub/Sub → SSE (Server-Sent Events) to browser |
| **Frontend** | Single-page HTML/CSS/JS, dark glassmorphic UI, SSE-driven real-time updates |
| **Observability** | Micrometer metrics, Logstash JSON structured logging, Spring Actuator |
| **ID Strategy** | All entities use String UUIDs (36 chars), auto-generated in `@PrePersist` |

### External Tool Dependencies (must be on PATH)

| Tool | Required By | Failure Mode |
|---|---|---|
| `yt-dlp` | YouTubeDownloader | OS-level process not found error |
| `ffmpeg` | MediaConverter, VideoEditor | OS-level process not found error |
| `ffprobe` | SmartRenderWorker (validation) | Returns 0.0 duration, may trigger validation failure |
| `nvidia-smi` | GpuHealthIndicator | Gracefully degrades, health stays UP |
| Python venv with `faster-whisper`, `torch` | TranscribeWorker | Subprocess exit code non-zero |

### Configuration Properties Map

| Property | Default | Used By |
|---|---|---|
| `clipper.queue.type` | `db` | QueueConfig — selects Redis vs DB queue |
| `google.api.key` | `""` (empty) | GeminiService — Gemini API auth |
| `gemini.model` | `gemini-1.5-flash` | GeminiService — model selection |
| `clipper.download.dir` | `data/temp/downloads` | YouTubeDownloader |
| `clipper.convert.dir` | `data/temp/converted` | MediaConverter |
| `clipper.cut.dir` | `data/temp/fragments` | VideoEditor |
| `clipper.cache.dir` | `data/library/cache` | TranscribeWorker, AnalyzeWorker |
| `clipper.library.video.dir` | `data/library/videos` | IngestWorker |
| `clipper.library.audio.dir` | `data/library/audios` | IngestWorker |
| `clipper.render.output.dir` | `data/jobs` | SmartRenderWorker |
| `clipper.python.path` | *(required)* | TranscribeWorker — path to Python exe |
| `clipper.python.env` | *(required)* | TranscribeWorker — working dir for Python |
| `youtube.cookies.path` | `""` (empty) | YouTubeDownloader — optional auth cookies |
| `ytdlp.format` | `bestvideo[height<=720]+bestaudio/best` | YouTubeDownloader |

---

## 2. END-TO-END EXECUTION TRACE

### Phase 0: System Bootstrap

```
JVM starts → ClipperApplication.main() → SpringApplication.run()
  → Component scanning under com.julius.clipper
  → Auto-configuration: JPA, Redis, Web, Actuator
  → @PostConstruct: WorkerRunner.init() populates worker map
  → @EventListener(ContextRefreshedEvent): WorkerRunner.start() → spawns pollingLoop on virtual thread
  → Static resources served: index.html, styles.css, app.js
```

### Phase 1: Job Submission (User → System)

```
Browser: User enters YouTube URL + config → clicks "Initialize Pipeline"
  ↓
app.js: submitJob() → POST /api/jobs
  Body: { url, count, min_duration, max_duration, template_ref, copy_language, language_mode }
  Header: X-User-Id: "6b10e02b-..." (hardcoded dev user)
  ↓
JobController.submitJob():
  1. Generate jobId = UUID
  2. Resolve userId from header (or generate random)
  3. Build Job entity: status=PENDING, config=JobConfig, correlationId="correlation-"+UUID[:8]
  4. Save Job to DB
  5. Create Task: type=DOWNLOAD, status=PENDING, payload={job_id, user_id, url, count, min/max_duration, copy_language}
  6. queueProvider.push(task) → enqueues to Redis or DB
  7. Return 202 ACCEPTED with {jobId}
  ↓
app.js: receives jobId → connectSSE(jobId)
  Opens EventSource to GET /api/jobs/{jobId}/stream
  ↓
JobController.streamJobEvents():
  Creates SseEmitter (30min timeout)
  Subscribes to Redis pub/sub channel "seone:job:{jobId}:events"
  Sends initial "subscribed" SSE event
```

### Phase 2: Pipeline Execution (Background)

The WorkerRunner's `pollingLoop` is already running on a virtual thread. Here's what happens:

#### Step 2a: DOWNLOAD Task

```
WorkerRunner.pollingLoop():
  Shuffles TaskType array for fairness
  Tries ioSemaphore.tryAcquire() for DOWNLOAD → acquired (8 permits available)
  queueProvider.pop(DOWNLOAD) → gets the task
  Submits executeTask(task) to virtual thread executor
  ↓
WorkerRunner.executeTask(task):
  1. orchestrator.setStartedAtAtomic(jobId) → Job.startedAt = now, status = PROCESSING (idempotent)
  2. orchestrator.updateCurrentStep(jobId, "DOWNLOAD")
  3. orchestrator.recordStepStart(jobId, "DOWNLOAD") → creates JobStep record
  4. eventPublisher.publish("step_started") → XADD to Redis Stream + PUBLISH to pub/sub → SSE to browser
  5. Start heartbeat scheduler: every 30s calls queueProvider.touchTaskHeartbeat(taskId)
  6. worker.process(task) → DownloadWorker.process(task)
```

```
DownloadWorker.process(task):
  1. Extract url, clip_id from payload
  2. Extract YouTube ID via regex (?:v=|\\/)([0-9A-Za-z_-]{11}).* or generate deterministic UUID
  3. downloader.probeVideo(url) → yt-dlp --dump-json → {id, title, duration}
  4. updateJobConfig(jobId, sourceTitle, clipId) → persist to DB
  5. Acquire distributed lock: "seone:lock:download:{clipId}" (polls every 2s, 5min max, 600s TTL)
  6. downloader.downloadAudio(url, "source_audio_"+clipId) → yt-dlp -f bestaudio → raw audio file
  7. converter.convertToWav(rawAudioPath) → ffmpeg → 16kHz mono PCM WAV
  8. Delete raw audio intermediate file
  9. Release distributed lock
  10. Return {storage_key: wavPath, clip_id: clipId}
```

```
Back in WorkerRunner.executeTask():
  7. worker.process() returned successfully
  8. recordStepCompletion(jobId, "DOWNLOAD", "completed")
  9. eventPublisher.publish("step_completed") → SSE to browser
  10. orchestrator.getNextTasks(task, result) → DAG routing
  ↓
Orchestrator.getNextTasks(DOWNLOAD):
  *** FORK POINT ***
  1. markForkEntered(jobId) → stamps Job.forkEnteredAt
  2. Creates Task(TRANSCRIBE): payload = {storage_key, clip_id, ...inherited}
  3. Creates Task(DOWNLOAD_VIDEO): payload = {clip_id, url, ...inherited}
  4. Returns [transcribeTask, downloadVideoTask]
  ↓
WorkerRunner pushes both tasks to queue
```

#### Step 2b: TRANSCRIBE + DOWNLOAD_VIDEO (Parallel Fork)

These two tasks are now both in the queue. The WorkerRunner's polling loop picks them up on separate virtual threads.

**TRANSCRIBE branch** (gpuSemaphore — only 1 permit):

```
TranscribeWorker.process(task):
  1. Check cache: data/library/cache/[userId_]{clipId}_transcript.json
     → If cache hit and valid: short-circuit return {transcript_key, clip_id}
  2. Validate audio file exists at storage_key path
  3. Create temp file whisper_raw_*.json
  4. Build command: [pythonPath, "scripts/transcribe_bridge.py", "--audio", audioPath, "--output", tempPath]
  5. Start Python subprocess (redirectErrorStream=true)
  6. Read all stdout line-by-line, log as "[Python Whisper] ..."
  7. Wait for completion (no explicit timeout in code — blocks indefinitely)
  8. Read raw segments JSON from temp file
  9. segmentMerger.mergeMaps(rawSegments, false):
     - Iterates segments, maintains buffer
     - Breaks on: silence gap > 700ms, segment > 10s, punctuation + min 1s
     - Emits MergedSegments with joined text
  10. Build full transcript string from merged segments
  11. Write {text, segments} JSON to cache file
  12. Return {transcript_key: cachePath, clip_id}
```

**DOWNLOAD_VIDEO branch** (ioSemaphore — 8 permits):

```
DownloadWorker.process(task): [same class, isVideoTask=true path]
  1. Acquire distributed lock (may skip if same clipId already downloaded)
  2. downloader.downloadVideo(url, "source_video_"+clipId)
     → yt-dlp -f "bestvideo[height<=720]+bestaudio/best" --merge-output-format mp4
     → 10-minute timeout
  3. Return {video_key: videoPath, clip_id}
```

Both branches complete independently. Each calls `orchestrator.getNextTasks()`:
- TRANSCRIBE → spawns ANALYZE task
- DOWNLOAD_VIDEO → enters the join barrier

#### Step 2c: ANALYZE + Join Barrier

```
AnalyzeWorker.process(task):
  1. Extract clip_id, transcript_key, top_n (default 2), min_duration (30s), max_duration (900s)
  2. Read transcript JSON file → {text, segments}
  3. geminiService.analyzeFullTranscript(fullText, segments, minDuration, maxDuration, topN, [])
     → Builds prompt: "You are a viral content expert..."
     → Constructs JSON schema for structured output
     → POST to https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
     → 3-minute timeout
     → Parses response: candidates[0].content.parts[0].text
     → Strips markdown fencing, deserializes to List<Map>
  4. extractAndVerifyClips(): validates schema (7 required fields), duration bounds, deduplicates
  5. If verified < topN: retry with exclusion ranges (second Gemini call)
  6. Write analysis to cache: [userId_]{clipId}_analysis.json
  7. Return {best_clips, clip_id, analysis_results_cache}
```

```
Back in Orchestrator.getNextTasks(ANALYZE):
  *** JOIN BARRIER ***
  1. Store analysis_results_cache key in Redis (1hr TTL)
  2. SADD "ANALYZE" to set "seone:{userId}:join:{jobId}:smart_render_prep"
  3. Check SCARD — if set size < 2, return empty (wait for DOWNLOAD_VIDEO)

Orchestrator.getNextTasks(DOWNLOAD_VIDEO):
  *** JOIN BARRIER ***
  1. Store video_key in Redis (1hr TTL)
  2. SADD "DOWNLOAD_VIDEO" to set "seone:{userId}:join:{jobId}:smart_render_prep"
  3. Check SCARD — now size == 2 → JOIN SATISFIED
  4. markJoinSatisfied(jobId) → stamps Job.joinSatisfiedAt
  5. getBestClipsFromResults() → retrieves clips from:
     a. task payload → b. Redis cache → c. hardcoded fallback
  6. Resolve sourceVideoKey from: Redis cache → result map → payload
  7. *** FAN-OUT ***
     For each clip (0..N-1):
       Create Task(SMART_RENDER):
         payload = {index, time_window: {start, end}, score, reasoning,
                    inputs: {pov_text: (pov_hi if hindi else pov_en)},
                    source_video_key, jobId, userId, ...}
  8. Return list of N SMART_RENDER tasks
```

#### Step 2d: SMART_RENDER (Fan-out, N parallel)

```
SmartRenderWorker.process(task):  [gpuSemaphore — only 1 at a time]
  1. Extract jobId, index, source_video_key, time_window{start, end}
  2. Validate source video file exists
  3. Build filename: clip-{index}_{titleSlug}_{templateSlug}_{jobId[:8]}.mp4
  4. videoEditor.cutVideo(sourcePath, start, end, filename):
     → ffmpeg -y -ss {start} -i {input} -t {duration}
       -c:v libx264 -preset ultrafast -crf 23
       -c:a aac -b:a 128k
       -map 0:v:0 -map 0:a?
       -movflags +faststart {output}
     → Dynamic timeout: max(120, min(600, duration * 5))
  5. Move generated fragment to data/jobs/{jobId}/clips/{filename}
  6. Validate: file > 1024 bytes
  7. probeVideoDuration(path): ffprobe subprocess → validate duration ≥ 5s
  8. Return {index, filename, storage_key, url, duration_seconds, size_bytes}
```

```
Back in Orchestrator.getNextTasks(SMART_RENDER):
  → Terminal step → calls registerClipOutput(jobId, result)

Orchestrator.registerClipOutput():
  1. Zombie guard: reject if job already terminal
  2. Idempotency: check if clip with same jobId+clipIndex already exists
  3. Save JobClip entity: {jobId, clipIndex, filename, storageKey, url, durationSeconds, sizeBytes}
  4. Count ready clips: jobClipRepository.countByJobId(jobId)
  5. Update job.clipsReady
  6. If readyCount >= job.clipCount:
     → job.status = COMPLETED, job.completedAt = now
     → eventPublisher.publish("job_completed", progress=100)
     → Micrometer counter: clipper.jobs.processed.total{status=SUCCESS}++
  7. Else:
     → eventPublisher.publish("progress_update", progress=calculated%)
```

### Phase 3: Completion (System → User)

```
EventPublisher.publish("job_completed"):
  1. INCR sequence counter in Redis
  2. XADD to Redis Stream "seone:{userId}:job:{jobId}:events"
  3. PUBLISH to "seone:job:{jobId}:events" (pub/sub channel)
  ↓
JobController: Redis MessageListener fires
  → SseEmitter.send(event) → SSE "progress" event to browser
  ↓
app.js: handlePipelineEvent({event_type: "job_completed"})
  1. Mark ALL tracker nodes as .completed (green)
  2. loadJobClips(jobId): GET /api/jobs/{jobId}/clips
  3. renderClips(): create video players with src="/data/jobs/{jobId}/clips/{filename}"
  4. Close SSE connection
  5. Reset submit button
```

### Data Shape Transformations

```
Phase 1: String(youtubeUrl) + JobConfig
  ↓ JobController
Phase 2a: Task.payload = {url, job_id, user_id, count, min_duration, max_duration}
  ↓ DownloadWorker
Phase 2b: {storage_key: "/abs/path/to/audio.wav", clip_id: "dQw4w9WgXcQ"}
  ↓ TranscribeWorker
Phase 2c: {transcript_key: "/abs/path/to/transcript.json"}
    transcript.json = {text: "full string", segments: [{start, end, text}, ...]}
  ↓ AnalyzeWorker
Phase 2d: {best_clips: [{start, end, score, reasoning, pov_en, pov_hi, text}, ...]}
  ↓ SmartRenderWorker (per clip)
Phase 2e: {index, filename, storage_key, url, duration_seconds, size_bytes}
  ↓ Orchestrator.registerClipOutput
Phase 3: JobClip entity persisted, Job.status → COMPLETED
```

---

## 3. PIPELINE DAG — THE REAL PICTURE

```
                    ┌─── TRANSCRIBE ─── ANALYZE ──────┐
  DOWNLOAD ──fork──┤                                   ├── JOIN BARRIER ── fan-out ── N × SMART_RENDER ── registerClipOutput
                    └─── DOWNLOAD_VIDEO ──────────────┘

  INGEST ──── TRANSCRIBE ──── ANALYZE ──── ⚠ HANGS (join barrier expects 2 branches, only 1 arrives)
```

### Fork-Join Implementation

**Fork:** `Orchestrator.getNextTasks(DOWNLOAD)` → creates 2 tasks, stamps `Job.forkEnteredAt`

**Join Barrier:** Redis SET `seone:{userId}:join:{jobId}:smart_render_prep`
- Each branch does `SADD` with its task type name
- After SADD, checks `SCARD` — if count == 2, join is satisfied
- The **second branch to complete** triggers the fan-out
- The first branch to complete returns an empty task list (waits)

**Fan-out:** Creates N `SMART_RENDER` tasks (one per clip from Gemini analysis)

### Resource Semaphores

| Pool | Permits | Task Types | Rationale |
|---|---|---|---|
| `ioSemaphore` | **8** | DOWNLOAD, DOWNLOAD_VIDEO, INGEST, ANALYZE | Network-bound, can parallelize |
| `cpuSemaphore` | **2** | CUT, LAYOUT | CPU-bound FFmpeg work |
| `gpuSemaphore` | **1** | TRANSCRIBE, SMART_RENDER | GPU memory is exclusive; only 1 Whisper or FFmpeg encode at a time |

### Task Queue Polling

```
WorkerRunner.pollingLoop():
  while (running):
    taskTypes = shuffle(TaskType.values())   // fairness
    foundWork = false
    for each type in taskTypes:
      sem = getSemaphore(type)
      if sem.tryAcquire():                   // non-blocking
        task = queueProvider.pop(type)
        if task != null:
          executor.submit(executeTaskWithRelease(task, sem))
          foundWork = true
        else:
          sem.release()                      // nothing in queue
    if !foundWork:
      Thread.sleep(1000)                     // backoff when idle
```

---

## 4. FILE-BY-FILE DECOMPOSITION

### 4.1 Entry Point

#### `ClipperApplication.java`
**Path:** `src/main/java/com/julius/clipper/ClipperApplication.java` (323 bytes)

```java
@SpringBootApplication
public class ClipperApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClipperApplication.class, args);
    }
}
```

- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`
- Triggers component scanning under `com.julius.clipper.*`
- Auto-configures: JPA (H2), Redis, Web (Tomcat), Actuator, Validation
- No custom logic. Pure bootstrap.

---

### 4.2 Domain Layer

#### `Job.java` — Central Domain Entity
**Path:** `src/main/java/com/julius/clipper/domain/Job.java` (3733 bytes)

The single most important entity. Every pipeline operation ultimately reads from or writes to a Job.

**Table:** `jobs` with indexes on `user_id`, `correlation_id`, `status`, `created_at`

**Fields:**

| Field | Type | Column | Default | Notes |
|---|---|---|---|---|
| `id` | `String` | `id` (length=36) | Auto-UUID | Primary key |
| `userId` | `String` | `user_id` (length=36) | — | Foreign key to users |
| `correlationId` | `String` | `correlation_id` (not null) | — | Trace ID for log correlation |
| `status` | `JobDBStatus` | `status` (not null, STRING) | `PENDING` | Current lifecycle state |
| `config` | `JobConfig` | `config` (TEXT, not null) | `new JobConfig()` | JSON-serialized via `JobConfigConverter` |
| `currentStep` | `String` | `current_step` | — | Name of active pipeline step |
| `clipsReady` | `int` | `clips_ready` (not null) | `0` | Count of rendered clips |
| `clipCount` | `int` | `clip_count` (not null) | `1` | Expected total clips |
| `createdAt` | `LocalDateTime` | `created_at` (not null, not updatable) | `now()` | Immutable creation timestamp |
| `startedAt` | `LocalDateTime` | `started_at` | — | Set when first task runs |
| `completedAt` | `LocalDateTime` | `completed_at` | — | Set on completion or failure |
| `errorMessage` | `String` | `error_message` (length=500) | — | Truncated error detail |
| `errorCode` | `String` | `error_code` | — | Machine-readable error code |
| `retryCount` | `int` | `retry_count` (not null) | `0` | Job-level retry counter |
| `idempotencyKey` | `String` | `idempotency_key` (unique) | — | Prevents duplicate submissions |
| `forkEnteredAt` | `LocalDateTime` | `fork_entered_at` | — | Stamped when DOWNLOAD forks |
| `joinSatisfiedAt` | `LocalDateTime` | `join_satisfied_at` | — | Stamped when both branches complete |

**Methods:**

- `@PrePersist onCreate()` — Auto-generates UUID `id` if null, sets `createdAt` if null
- `deriveStatus()` → Priority-based status derivation: CANCELLED > FAILED (if error) > COMPLETED (if completedAt) > PROCESSING (if startedAt) > PENDING
- `derivePhase()` → Human-readable: "cancelled" / "failed" / "completed" / "rendering" / "forked" / "downloading" / "queued"
- `toApiStatus()` → Lowercased status name

**State Machine:**

```
PENDING ──(first task picked up)──→ PROCESSING ──(all clips rendered)──→ COMPLETED
    │                                    │
    │                                    └──(any step fails)──→ FAILED
    │
    └──(cancelled by user/system)──→ CANCELLED
```

---

#### `Task.java` — Pipeline Work Unit
**Path:** `src/main/java/com/julius/clipper/domain/Task.java` (2442 bytes)

The unit of work that flows through the queue. Each pipeline step creates Task entities.

**Table:** `tasks`

**Fields:**

| Field | Type | Column | Default | Notes |
|---|---|---|---|---|
| `id` | `String` | `id` (length=36) | Auto-UUID | Primary key |
| `type` | `TaskType` | `type` (not null, STRING) | — | Pipeline step enum |
| `payload` | `Map<String, Object>` | `payload` (TEXT) | `new HashMap<>()` | JSON-serialized via `MapJsonConverter` |
| `status` | `TaskStatus` | `status` (not null, STRING) | `PENDING` | Task lifecycle enum |
| `createdAt` | `LocalDateTime` | `created_at` (not null) | `now()` | — |
| `updatedAt` | `LocalDateTime` | `updated_at` (not null) | `now()` | Auto-refreshed via `@PreUpdate` |
| `startedAt` | `LocalDateTime` | `started_at` | — | Set when dequeued |
| `retries` | `int` | `retries` (not null) | `0` | Retry counter |
| `error` | `String` | `error` (length=1000) | — | Error message on failure |

**Derived Properties (via `@JsonIgnore`):**

- `getUserId()` → extracts `"user_id"` from payload map
- `getJobId()` → extracts `"job_id"` from payload map

**Key Design:** The payload is a generic `Map<String, Object>`. There's no typed DTO per task type — all inter-step data is passed as string-keyed maps. This makes the pipeline flexible but sacrifices compile-time type safety.

---

#### `Clip.java` — Source Video Record
**Path:** `src/main/java/com/julius/clipper/domain/Clip.java` (1822 bytes)

Represents a source video/audio asset in the system library.

**Table:** `clips`

**Fields:**

| Field | Type | Column | Default | Notes |
|---|---|---|---|---|
| `id` | `String` | `id` (length=36) | Auto-UUID | Primary key |
| `sourceUrl` | `String` | `source_url` | — | Original YouTube URL |
| `sourceType` | `String` | `source_type` | `"other"` | "youtube", "upload", etc. |
| `storageKey` | `String` | `storage_key` | — | Filesystem path to stored asset |
| `status` | `String` | `status` | `"pending"` | Free-form status string |
| `metadataInfo` | `Map<String, Object>` | `metadata_info` (TEXT) | `new HashMap<>()` | JSON metadata blob |
| `analysisResults` | `Map<String, Object>` | `analysis_results` (TEXT) | `new HashMap<>()` | JSON analysis blob |
| `createdAt` | `LocalDateTime` | `created_at` (not null, not updatable) | `now()` | — |
| `updatedAt` | `LocalDateTime` | `updated_at` (not null) | `now()` | Auto-refreshed |

---

#### `JobClip.java` — Rendered Output Record
**Path:** `src/main/java/com/julius/clipper/domain/JobClip.java` (1494 bytes)

Links a Job to a specific rendered clip output. One Job produces N JobClips.

**Table:** `job_clips` with unique constraints on `(job_id, clip_index)` and `(job_id, filename)`, index on `job_id`

**Fields:**

| Field | Type | Column | Default | Notes |
|---|---|---|---|---|
| `id` | `String` | `id` (length=36) | Auto-UUID | Primary key |
| `jobId` | `String` | `job_id` (not null) | — | FK to jobs |
| `clipIndex` | `int` | `clip_index` (not null) | — | 1-based clip ordinal |
| `filename` | `String` | `filename` (not null) | — | Output filename |
| `storageKey` | `String` | `storage_key` | — | Filesystem path |
| `url` | `String` | `url` | — | HTTP URL for download |
| `durationSeconds` | `Double` | `duration_seconds` | — | Clip duration |
| `sizeBytes` | `Long` | `size_bytes` | — | File size |
| `createdAt` | `LocalDateTime` | `created_at` (not null, not updatable) | `now()` | — |

The `(job_id, clip_index)` unique constraint provides the idempotency guard used by `Orchestrator.registerClipOutput()`.

---

#### `JobStep.java` — Pipeline Step Audit Record
**Path:** `src/main/java/com/julius/clipper/domain/JobStep.java` (1466 bytes)

Records the execution of each pipeline step for a job. Used for the timeline display in the UI.

**Table:** `job_steps` with unique constraint on `(job_id, step_type)`, index on `job_id`

**Fields:**

| Field | Type | Column | Default | Notes |
|---|---|---|---|---|
| `id` | `String` | `id` (length=36) | Auto-UUID | Primary key |
| `jobId` | `String` | `job_id` (not null) | — | FK to jobs |
| `stepType` | `String` | `step_type` (not null) | — | "DOWNLOAD", "TRANSCRIBE", etc. |
| `status` | `String` | `status` (not null) | `"pending"` | "pending"/"running"/"completed"/"failed" |
| `startedAt` | `LocalDateTime` | `started_at` | — | — |
| `completedAt` | `LocalDateTime` | `completed_at` | — | — |
| `errorMessage` | `String` | `error_message` (length=1000) | — | — |
| `stepMetadata` | `Map<String, Object>` | `step_metadata` (TEXT) | `new HashMap<>()` | JSON blob |

---

#### `User.java`
**Path:** `src/main/java/com/julius/clipper/domain/User.java` (829 bytes)

Minimal user entity. Currently used mostly for ID propagation through the pipeline.

**Table:** `users`

**Fields:** `id` (String, UUID), `email` (String, unique, not null), `fullName` (String), `createdAt` (LocalDateTime)

---

#### `UxFact.java` — Dynamic UI Content
**Path:** `src/main/java/com/julius/clipper/domain/UxFact.java` (2079 bytes)

Stores UX copy/facts for dynamic frontend content (e.g., "Did you know?" cards). Not related to pipeline events despite the name suggesting analytics.

**Table:** `ux_facts` with indexes on `slot`, `language`, `audience_scope`, `enabled`, `created_at`

**Fields:**

| Field | Type | Column | Default | Notes |
|---|---|---|---|---|
| `id` | `String` | `id` (length=36) | Auto-UUID | — |
| `slot` | `String` | `slot` (not null) | — | UI placement slot |
| `language` | `String` | `language` (not null) | `"en"` | Language code |
| `audienceScope` | `String` | `audience_scope` (not null) | `"global"` | Targeting scope |
| `headline` | `String` | `headline` (not null) | — | Display headline |
| `body` | `String` | `body` (not null) | — | Display body text |
| `tag` | `String` | `tag` (not null) | `"Did you know?"` | Category tag |
| `canonicalHash` | `String` | `canonical_hash` (unique, not null) | — | Exact dedup hash |
| `nearDupeHash` | `String` | `near_dupe_hash` (not null) | — | Fuzzy dedup hash |
| `sourceModel` | `String` | `source_model` | — | LLM that generated it |
| `enabled` | `boolean` | `enabled` (not null) | `true` | Active flag |
| `usedCount` | `int` | `used_count` (not null) | `0` | Impression counter |
| `createdAt` | `LocalDateTime` | `created_at` (not null, not updatable) | `now()` | — |

---

#### `JobDBStatus.java` — Job Lifecycle Enum
**Path:** `src/main/java/com/julius/clipper/domain/JobDBStatus.java` (134 bytes)

```java
public enum JobDBStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
}
```

---

#### `JobConfig.java` — Job Configuration DTO
**Path:** `src/main/java/com/julius/clipper/domain/dto/JobConfig.java` (932 bytes)

Not a JPA entity. Serialized to JSON and stored in `Job.config` column.

**Fields:**

| Field | Type | `@JsonProperty` | Notes |
|---|---|---|---|
| `url` | `String` | — | Source URL |
| `count` | `int` | — | Number of clips to generate |
| `minDuration` | `double` | `"min_duration"` | Minimum clip duration (seconds) |
| `maxDuration` | `double` | `"max_duration"` | Maximum clip duration (seconds) |
| `templateRef` | `String` | `"template_ref"` | Render template reference |
| `languageMode` | `String` | `"language_mode"` | "auto"/"mixed"/explicit |
| `copyLanguage` | `String` | `"copy_language"` | Language for POV caption text |
| `renderOptions` | `Map<String, Object>` | `"render_options"` | Additional render params |
| `sourceTitle` | `String` | `"source_title"` | Video title (populated by DownloadWorker) |
| `sourceClipId` | `String` | `"source_clip_id"` | Canonical clip ID (populated by DownloadWorker) |
| `requestedCount` | `Integer` | `"requested_count"` | Original requested count |

---

#### `JobConfigConverter.java`
**Path:** `src/main/java/com/julius/clipper/domain/converter/JobConfigConverter.java` (1264 bytes)

JPA `AttributeConverter<JobConfig, String>`. `@Converter(autoApply = true)`.

- `convertToDatabaseColumn(JobConfig)` → `objectMapper.writeValueAsString()`, returns `"{}"` for null
- `convertToEntityAttribute(String)` → `objectMapper.readValue()`, returns `new JobConfig()` for null/blank/`"{}"`
- Wraps `JsonProcessingException` in `RuntimeException`

---

#### `MapJsonConverter.java`
**Path:** `src/main/java/com/julius/clipper/domain/converter/MapJsonConverter.java` (1358 bytes)

JPA `AttributeConverter<Map<String, Object>, String>`. `@Converter` (NOT auto-apply — must be referenced with `@Convert`).

Same pattern as `JobConfigConverter` but for generic `Map<String, Object>`. Uses `TypeReference<Map<String, Object>>()` for deserialization.

---

### 4.3 Pipeline Infrastructure

#### `TaskType.java` — Pipeline Step Types
**Path:** `src/main/java/com/julius/clipper/pipeline/TaskType.java` (176 bytes)

```java
public enum TaskType {
    DOWNLOAD, DOWNLOAD_VIDEO, INGEST, TRANSCRIBE, ANALYZE, CUT, LAYOUT, SMART_RENDER
}
```

| Value | Role | Semaphore Pool |
|---|---|---|
| `DOWNLOAD` | Downloads audio from URL | IO (8) |
| `DOWNLOAD_VIDEO` | Downloads video from URL (fork branch) | IO (8) |
| `INGEST` | Local file ingestion (alternative entry) | IO (8) |
| `TRANSCRIBE` | Whisper transcription | GPU (1) |
| `ANALYZE` | Gemini viral analysis | IO (8) |
| `CUT` | Legacy clip cut (terminal) | CPU (2) |
| `LAYOUT` | No real worker — mock fallback | CPU (2) |
| `SMART_RENDER` | Full clip render (terminal) | GPU (1) |

---

#### `TaskStatus.java` — Task Lifecycle Enum
**Path:** `src/main/java/com/julius/clipper/pipeline/TaskStatus.java` (135 bytes)

```java
public enum TaskStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
}
```

---

#### `Worker.java` — Worker Interface
**Path:** `src/main/java/com/julius/clipper/pipeline/Worker.java` (189 bytes)

```java
public interface Worker {
    Map<String, Object> process(Task task) throws Exception;
}
```

Functional interface. Input: Task entity. Output: result map. Exceptions propagate to WorkerRunner which handles failure.

---

#### `QueueProvider.java` — Queue Abstraction
**Path:** `src/main/java/com/julius/clipper/pipeline/QueueProvider.java` (290 bytes)

```java
public interface QueueProvider {
    void push(Task task);
    Task pop(TaskType taskType);
    void complete(Task task);
    void fail(String taskId, String error);
    void touchTaskHeartbeat(String taskId);
}
```

Two implementations: `RedisQueue` (production) and `DbQueue` (testing).

---

#### `DbQueue.java` — Database Queue Implementation
**Path:** `src/main/java/com/julius/clipper/pipeline/DbQueue.java` (1985 bytes)

`@Service`, implements `QueueProvider`. Used when `clipper.queue.type=db` (the default).

**Methods (all `@Transactional`):**

| Method | Logic |
|---|---|
| `push(task)` | Sets status=PENDING, saves to DB |
| `pop(taskType)` | `taskRepository.findFirstForUpdateSkipLocked(type, PENDING, PageRequest.of(0,1))` → sets status=PROCESSING, stamps startedAt/updatedAt |
| `complete(task)` | Deletes task row entirely |
| `fail(taskId, error)` | Sets status=FAILED, sets error, stamps updatedAt |
| `touchTaskHeartbeat(taskId)` | Stamps updatedAt |

**Critical Design:** `findFirstForUpdateSkipLocked` uses `@Lock(PESSIMISTIC_WRITE)` with `@QueryHint(lock.timeout = -2)` which maps to PostgreSQL's `SKIP LOCKED`. This means concurrent workers don't block — they simply skip locked rows. This is the correct pattern for competing consumers.

---

#### `RedisQueue.java` — Redis Queue Implementation
**Path:** `src/main/java/com/julius/clipper/pipeline/RedisQueue.java` (8332 bytes)

`@Service`, implements `QueueProvider`. Used when `clipper.queue.type=redis`.

**Redis Key Schema:**

| Key Pattern | Type | Purpose |
|---|---|---|
| `seone:task:{taskId}` | HASH | Task data (payload, user_id, type, status, timestamps) |
| `seone:{userId}:queue:{TYPE}` | LIST | Per-tenant pending queue |
| `seone:queue:{TYPE}` | LIST | Legacy pending queue (no userId) |
| `seone:{userId}:processing:{TYPE}` | LIST | Per-tenant in-flight queue |
| `seone:processing:{TYPE}` | LIST | Legacy in-flight queue |
| `seone:active_tenants` | SET | All user IDs with queued work |
| `seone:pending_signal:{TYPE}` | LIST | KEDA autoscaler signal |
| `seone:processing_signal:{TYPE}` | LIST | KEDA autoscaler signal |
| `seone:{userId}:dlq` / `seone:dlq` | LIST | Dead-letter queue (max 10,000) |

**push(task):**
1. Auto-generate UUID if null
2. Serialize task to JSON, store in HASH with 7-day TTL
3. If userId present: LPUSH to tenant queue, SADD to active_tenants, LPUSH to KEDA signal
4. If no userId: LPUSH to legacy queue

**pop(taskType) — Multi-Tenant Fair Polling:**
1. Fetch all members of `seone:active_tenants`, **shuffle** for fairness
2. For each tenant: `RPOPLPUSH` from tenant's pending queue → tenant's processing queue
3. If task found: remove from pending_signal, add to processing_signal, hydrate task from HASH
4. Fallback: try legacy queue with same `RPOPLPUSH` pattern
5. Return null if nothing found

**complete(task):**
1. LREM from processing list, LREM from processing_signal
2. DELETE the task hash

**fail(taskId, error):**
1. Read user_id/type from task hash
2. LREM from processing list/signal
3. Build DLQ entry JSON with id, error, user_id, failed_at timestamps
4. RPUSH to DLQ list, LTRIM to 10,000 entries
5. DELETE the task hash
6. Note: DLQ creation errors are **silently swallowed** (empty catch block)

**touchTaskHeartbeat(taskId):**
- HSET `heartbeat_at` field to current epoch seconds

**hydrateTask(taskId):**
- HGETALL from task hash
- Deserializes payload JSON back to Task object
- Syncs status from hash
- Converts started_at epoch to LocalDateTime using system timezone

---

#### `EventPublisher.java` — Real-Time Event System
**Path:** `src/main/java/com/julius/clipper/pipeline/EventPublisher.java` (3997 bytes)

`@Service`. Central event publishing hub. Writes to Redis Streams + Redis Pub/Sub.

**Single method: `publish(jobId, userId, eventType, payload, step, progress, message, traceId)`**

Execution:
1. **Resolve userId** — 3-level fallback: argument → payload map → DB lookup (jobRepository.findById)
2. **Sequence counter** — `INCR seone:{userId}:job:{jobId}:seq` (monotonically increasing for ordered replay)
3. **Build event fields** — Map with: `event_id` (UUID), `seq`, `job_id`, `user_id`, `event_type`, `timestamp` (ISO), `step`, `progress`, `message`, `payload` (JSON string), `trace_id`
4. **XADD to Redis Stream** — Key: `seone:{userId}:job:{jobId}:events`, approximate trim to 20,000 entries
5. **Dual Pub/Sub publish** — Channel `seone:job_events` (global) + `seone:job:{jobId}:events` (per-job)
6. **Fire-and-forget** — Entire method wrapped in try/catch, logs error but NEVER throws

**Event types used in codebase:** `step_started`, `step_completed`, `job_completed`, `job_failed`, `progress_update`

---

#### `Orchestrator.java` — The Pipeline Brain
**Path:** `src/main/java/com/julius/clipper/pipeline/Orchestrator.java` (19675 bytes)

`@Service`. The central DAG routing engine. Does NOT execute work — only routes tasks and manages job lifecycle.

**Dependencies (8):** `JobRepository`, `JobClipRepository`, `JobStepRepository`, `TaskRepository`, `EventPublisher`, `StringRedisTemplate`, `MeterRegistry`, `ObjectMapper`

**Core Method: `getNextTasks(Task currentTask, Map<String,Object> result)` — `@Transactional`**

This is the DAG routing engine. Given a completed task and its result, returns the list of downstream tasks to enqueue.

**Zombie Guard (always first):** Checks if job is terminal (COMPLETED/FAILED/CANCELLED). If so, returns empty list. Prevents stale task completions from spawning new work.

**Routing Table:**

| Completed TaskType | Action | Downstream Tasks |
|---|---|---|
| DOWNLOAD | **Fork** — stamps `forkEnteredAt` | [TRANSCRIBE, DOWNLOAD_VIDEO] |
| INGEST | Linear | [TRANSCRIBE] |
| TRANSCRIBE | Linear | [ANALYZE] |
| ANALYZE | **Join barrier** — SADD to Redis SET, check SCARD | If count < 2: [] (wait). If count == 2: [N × SMART_RENDER] |
| DOWNLOAD_VIDEO | **Join barrier** — same as ANALYZE | Same as above |
| CUT / SMART_RENDER | **Terminal** — calls `registerClipOutput()` | [] |

**Join Barrier Detail (ANALYZE/DOWNLOAD_VIDEO cases):**

```
1. If DOWNLOAD_VIDEO: store video_key in Redis ("seone:job:{jobId}:video_key", 1hr TTL)
2. If ANALYZE: store analysis_results_cache in Redis (1hr TTL)
3. SADD task type name to "seone:{userId}:join:{jobId}:smart_render_prep"
4. SCARD the set
5. If count == 2:
   a. markJoinSatisfied(jobId) → stamps joinSatisfiedAt
   b. getBestClipsFromResults() → 3-level fallback: task payload → Redis cache → hardcoded default
   c. If actual clips < requested: adjustExpectedClipCount() → prevents completion check from hanging
   d. Resolve sourceVideoKey from: Redis → result map → payload
   e. Fan-out: create N SMART_RENDER tasks, each with index, time_window, score, reasoning, pov_text
6. Return task list
```

**registerClipOutput(jobId, clipInfo) — `@Transactional`:**

```
1. Zombie guard
2. Extract: index, filename, storage_key (strip "data/" prefix), url, duration, size
3. Idempotency: check jobClipRepository.findByJobIdAndClipIndex() — skip if exists
4. Save JobClip entity
5. Count total: jobClipRepository.countByJobId(jobId)
6. Update job.clipsReady
7. If readyCount >= job.clipCount:
   → COMPLETED + job_completed event + Micrometer counter SUCCESS
8. Else:
   → progress_update event
```

**markJobFailed(jobId, error, userId) — `@Transactional`:**

```
1. Zombie guard
2. Set job status=FAILED, truncate error to 500 chars, stamp completedAt
3. Redis cleanup: KEYS "seone:{userId}:join:{jobId}:*" → DELETE all
4. Cancel outstanding tasks: taskRepository.findAll() → iterate → cancel matching jobId tasks
   ⚠ Performance: findAll() scans entire task table
5. Publish job_failed event
6. Micrometer counter FAILED
```

**Supporting methods:**
- `setStartedAtAtomic(jobId)` — Idempotent: only sets startedAt + PROCESSING if startedAt is null
- `updateCurrentStep(jobId, stepName)` — Updates Job.currentStep
- `recordStepStart(jobId, stepName)` — Creates/updates JobStep record
- `recordStepCompletion(jobId, stepName, status, error)` — Updates JobStep
- `markForkEntered(jobId)` — Stamps forkEnteredAt, records "fork:entered" step
- `markJoinSatisfied(jobId)` — Stamps joinSatisfiedAt, records "join:smart_render_prep" step
- `adjustExpectedClipCount(jobId, count)` — Updates job.clipCount
- `getBestClipsFromResults(userId, jobId, task)` — 3-level data resolution with hardcoded fallback

**getBestClipsFromResults fallback clip:**
```json
{
  "start": 10, "end": 40, "score": 90,
  "reasoning": "Engaging hook and clear climax",
  "pov_en": "Wait for the twist...",
  "pov_hi": "ट्विस्ट का इंतजार करें..."
}
```

---

#### `WorkerRunner.java` — The Execution Engine
**Path:** `src/main/java/com/julius/clipper/pipeline/WorkerRunner.java` (10251 bytes)

`@Service`. Polls queues, dispatches tasks to workers, manages lifecycle.

**Fields:**

| Field | Type | Notes |
|---|---|---|
| `queueProvider` | `QueueProvider` | Redis or DB queue |
| `orchestrator` | `Orchestrator` | DAG router |
| `eventPublisher` | `EventPublisher` | Event system |
| `meterRegistry` | `MeterRegistry` | Metrics |
| `workers` | `Map<TaskType, Worker>` | Task type → worker mapping |
| `ioSemaphore` | `Semaphore(8)` | IO-bound tasks |
| `cpuSemaphore` | `Semaphore(2)` | CPU-bound tasks |
| `gpuSemaphore` | `Semaphore(1)` | GPU-bound tasks |
| `executorService` | `ExecutorService` | `Executors.newVirtualThreadPerTaskExecutor()` — Java 21 virtual threads |
| `activeWorkersCount` | `AtomicInteger` | Gauge backing |
| `running` | `volatile boolean` | Lifecycle flag |
| `applicationContext` | `ApplicationContext` | For dynamic bean lookup |

**Lifecycle:**

```
@PostConstruct init():
  1. Look up worker beans by name: DOWNLOADWorker, INGESTWorker, TRANSCRIBEWorker, ANALYZEWorker, SMART_RENDERWorker
  2. DOWNLOADWorker handles both DOWNLOAD and DOWNLOAD_VIDEO
  3. On lookup failure: log warning (don't throw)
  4. Install mock fallback for any unresolved TaskType: task -> Map.of("status", "mock_completed")

@EventListener(ContextRefreshedEvent.class):
  → Calls start()

start() [synchronized]:
  → Sets running=true, submits pollingLoop to virtual thread

stop() [synchronized]:
  → Sets running=false, shuts down executor
```

**executeTask(task) — The Critical Path:**

```
try:
  1. STEP START:
     orchestrator.setStartedAtAtomic(jobId)       // Mark job PROCESSING
     orchestrator.updateCurrentStep(jobId, step)   // Update current step
     orchestrator.recordStepStart(jobId, step)     // Create JobStep record
     eventPublisher.publish("step_started")        // SSE to browser

  2. HEARTBEAT:
     ScheduledExecutorService → every 30s: queueProvider.touchTaskHeartbeat(taskId)

  3. EXECUTE:
     worker = workers.get(task.type)
     result = worker.process(task)                 // THE ACTUAL WORK

  4. SUCCESS:
     recordStepCompletion("completed")
     publish("step_completed")
     nextTasks = orchestrator.getNextTasks(task, result)    // DAG routing
     for each nextTask: queueProvider.push(nextTask)        // Enqueue downstream
     queueProvider.complete(task)                            // ACK

catch Exception:
  5. FAILURE:
     queueProvider.fail(taskId, error)             // DLQ
     recordStepCompletion("failed")
     orchestrator.markJobFailed(jobId, error, userId)  // Cascade: cancel tasks, emit event

finally:
  6. CLEANUP:
     heartbeatScheduler.shutdownNow()
     Timer: clipper.task.execution.duration{type=X, percentiles=p50,p90,p95,p99}
     Counter: clipper.tasks.processed.total{type=X, status=SUCCESS|FAILED}
     activeWorkersCount.decrementAndGet()
     semaphore.release()
```

---

### 4.4 Worker Implementations

#### `DownloadWorker.java` — Audio/Video Download
**Path:** `src/main/java/com/julius/clipper/pipeline/worker/DownloadWorker.java` (6537 bytes)

`@Component("DOWNLOADWorker")`. Handles both `DOWNLOAD` (audio) and `DOWNLOAD_VIDEO` (video) task types.

**Dependencies:** `YouTubeDownloader`, `MediaConverter`, `JobRepository`, `DistributedLockManager`

**process(task) pseudocode:**

```
payload = task.getPayload()
url = payload["url"]
clipId = payload["clip_id"]
isVideoTask = (task.type == DOWNLOAD_VIDEO)

// Step 1: Extract/compute clipId
if clipId is null:
  clipId = extractYouTubeId(url)  // regex: 11-char YouTube ID
  if clipId is null:
    clipId = deterministicUUID(url.bytes)[:12]

// Step 2: Probe metadata (non-fatal on failure)
try:
  metadata = downloader.probeVideo(url)  // yt-dlp --dump-json
  if metadata["id"] != clipId: clipId = metadata["id"]
  sourceTitle = metadata["title"] || metadata["fulltitle"] || "Untitled"
  updateJobConfig(jobId, sourceTitle, clipId)
catch: log.warn("Probe failed, continuing anyway")

// Step 3: Distributed lock
lockKey = "seone:lock:download:" + clipId
ownerId = UUID.random()
locked = false
for 5 minutes, every 2 seconds:
  locked = lockManager.acquireLock(lockKey, ownerId, 600)  // 600s TTL
  if locked: break
if !locked: throw RuntimeException("Lock timeout")

try:
  if isVideoTask:
    path = downloader.downloadVideo(url, "source_video_" + clipId)
    return {video_key: path, clip_id: clipId}
  else:
    rawPath = downloader.downloadAudio(url, "source_audio_" + clipId)
    wavPath = converter.convertToWav(rawPath)
    if rawPath != wavPath: delete(rawPath)  // cleanup intermediate
    return {storage_key: wavPath, clip_id: clipId}
finally:
  lockManager.releaseLock(lockKey, ownerId)
```

---

#### `IngestWorker.java` — Local File Ingestion
**Path:** `src/main/java/com/julius/clipper/pipeline/worker/IngestWorker.java` (4785 bytes)

`@Component("INGESTWorker")`. Alternative entry point for locally uploaded files (skips YouTube download).

**Dependencies:** `MediaConverter`, `@Value(clipper.library.video.dir)`, `@Value(clipper.library.audio.dir)`

**process(task) pseudocode:**

```
filePath = payload["file_path"]
userId = payload["user_id"]
validate file exists

// SHA-256 content-based deduplication
sha256 = calculateSHA256(file)
clipId = "file_" + sha256[:16]

// Copy video to library (content-addressable)
filename = [userId + "_"] + clipId + extension
targetPath = libraryVideoDir / filename
if !exists(targetPath): Files.copy(source, target, REPLACE_EXISTING)
else: log("Already in library, skipping copy")

// Extract audio
wavTemp = converter.convertToWav(targetPath)
audioFilename = [userId + "_"] + clipId + ".wav"
audioTarget = libraryAudioDir / audioFilename
if !exists(audioTarget): Files.move(wavTemp, audioTarget)
else: Files.delete(wavTemp)

return {video_key: targetPath, storage_key: audioTarget, clip_id: clipId}
```

---

#### `TranscribeWorker.java` — Whisper Transcription
**Path:** `src/main/java/com/julius/clipper/pipeline/worker/TranscribeWorker.java` (7215 bytes)

`@Component("TRANSCRIBEWorker")`. Runs Python faster-whisper as a subprocess.

**Dependencies:** `SegmentMerger`, `@Value(clipper.cache.dir)`, `@Value(clipper.python.path)`, `@Value(clipper.python.env)`

**process(task) pseudocode:**

```
clipId = payload["clip_id"]
audioKey = payload["storage_key"]
userId = payload["user_id"]

// Step 1: Cache check
cacheFile = cacheDir / [userId + "_"] + clipId + "_transcript.json"
if cacheFile.exists():
  content = read(cacheFile)
  if content.length > 10:
    map = deserialize(content)
    if map has "segments" or "text":
      return {transcript_key: cacheFile, clip_id: clipId}  // CACHE HIT

// Step 2: Validate audio
if !exists(audioKey): throw FileNotFoundException

// Step 3: Python subprocess
tempFile = createTempFile("whisper_raw_", ".json")
tempFile.deleteOnExit()
pythonExe = resolve(pythonPath)  // handle relative paths
command = [pythonExe, "scripts/transcribe_bridge.py", "--audio", audioKey, "--output", tempFile]
workDir = resolve(pythonPathEnv) || user.dir
process = ProcessBuilder(command).directory(workDir).redirectErrorStream(true).start()
readAllOutput(process.inputStream)  // logs as "[Python Whisper] ..."
process.waitFor()  // NO TIMEOUT — blocks indefinitely ⚠
if exitCode != 0: throw RuntimeException

// Step 4: Read & merge segments
rawSegments = deserialize(tempFile)  // List<Map>
mergedSegments = segmentMerger.mergeMaps(rawSegments, false)

// Step 5: Build full transcript
fullText = mergedSegments.map(s -> s["text"]).join(" ")
result = {text: fullText, segments: mergedSegments}

// Step 6: Persist cache
write(cacheFile, prettyPrint(result))
return {transcript_key: cacheFile, clip_id: clipId}
```

---

#### `AnalyzeWorker.java` — Gemini AI Analysis
**Path:** `src/main/java/com/julius/clipper/pipeline/worker/AnalyzeWorker.java` (8044 bytes)

`@Component("ANALYZEWorker")`. Sends transcript to Gemini, validates/retries clip suggestions.

**Dependencies:** `GeminiService`, `@Value(clipper.cache.dir)`

**process(task) pseudocode:**

```
clipId = payload["clip_id"]
transcriptKey = payload["transcript_key"]
userId = payload["user_id"]
topN = payload["top_n"] || 2
minDuration = payload["min_duration"] || 30.0
maxDuration = payload["max_duration"] || 900.0

// Step 1: Load transcript
transcript = deserialize(read(transcriptKey))
fullText = transcript["text"]
segments = transcript["segments"]

// Step 2: First Gemini call
rawClips = geminiService.analyzeFullTranscript(fullText, segments, minDuration, maxDuration, topN, [])

// Step 3: Verify clips
processedWindows = new HashSet<String>()
verifiedClips = extractAndVerifyClips(rawClips, minDuration, maxDuration, processedWindows)
  // For each clip:
  //   1. verifyClipSchema() — checks 7 required fields: start, end, score, reasoning, pov_en, pov_hi, text
  //   2. Checks start/end are Number instances
  //   3. Checks duration in [minDuration, maxDuration]
  //   4. Deduplicates by "%.2f_%.2f" time window key

// Step 4: Retry if insufficient
if verifiedClips.size() < topN:
  missingCount = topN - verifiedClips.size()
  exclusions = verifiedClips.map(c -> {start, end})
  try:
    retryClips = geminiService.analyzeFullTranscript(..., missingCount, exclusions)
    retryVerified = extractAndVerifyClips(retryClips, ...)
    verifiedClips.addAll(retryVerified)
  catch: log.error("Retry failed, continuing with partial results")

// Step 5: Truncate & cache
if verifiedClips.size() > topN: verifiedClips = verifiedClips[:topN]
cacheData = {best_clips, requested_clips: topN, returned_clips: actual, partial_results: bool}
write(cacheDir / [userId_]{clipId}_analysis.json, cacheData)

return {best_clips: verifiedClips, clip_id: clipId, analysis_results_cache: cachePath}
```

---

#### `SmartRenderWorker.java` — Video Clip Rendering
**Path:** `src/main/java/com/julius/clipper/pipeline/worker/SmartRenderWorker.java` (7153 bytes)

`@Component("SMART_RENDERWorker")`. Cuts a time-range clip from source video, validates output.

**Dependencies:** `VideoEditor`, `@Value(clipper.render.output.dir)`

**process(task) pseudocode:**

```
jobId = payload["job_id"]
index = payload["index"] || 1
sourceVideoKey = payload["source_video_key"]
sourceTitle = payload["source_title"]
templateRef = payload["template_ref"]
timeWindow = payload["time_window"]  // {start, end}
start = timeWindow["start"]
end = timeWindow["end"]
duration = end - start

// Step 1: Validate source
if sourceVideoKey is null: throw FileNotFoundException
if !exists(sourceVideoKey): throw FileNotFoundException

// Step 2: Build filename
clipFilename = buildClipFilename(jobId, index, sourceTitle, templateRef)
  // Format: clip-{02d}_{titleSlug}_{templateSlug}_{jobId[:8]}.mp4
  // Slugify: lowercase → remove non-alnum → spaces to hyphens → collapse hyphens

// Step 3: Cut video
destDir = outputDir / jobId / clips /
mkdir(destDir)
fragmentPath = videoEditor.cutVideo(sourceVideoKey, start, end, clipFilename)
destPath = destDir / clipFilename
Files.move(fragmentPath, destPath, REPLACE_EXISTING)

// Step 4: Validate output
if !exists(destPath) || size < 1024: throw RuntimeException

probedDuration = probeVideoDuration(destPath)
  // ffprobe -v error -show_entries format=duration ... → parse double
  // 10-second timeout on ffprobe
  // Returns 0.0 on any failure
if probedDuration < 5.0: throw RuntimeException("Clip too short")
if probedDuration < 0.5 * duration: log.warn("Duration mismatch")

// Step 5: Build output
return {
  index: index,
  filename: clipFilename,
  storage_key: destPath.absolutePath,
  url: "http://localhost:8080/data/jobs/{jobId}/clips/{clipFilename}",  // HARDCODED ⚠
  duration_seconds: probedDuration,
  size_bytes: file.length()
}
```

---

### 4.5 Service Layer

#### `GeminiService.java` — AI Integration
**Path:** `src/main/java/com/julius/clipper/service/GeminiService.java` (17713 bytes)

`@Service`. Integrates with Google Gemini API for both transcript analysis and audio transcription.

**Fields:**

| Field | Type | Source |
|---|---|---|
| `apiKey` | `String` | `@Value("${google.api.key:}")` |
| `modelName` | `String` | `@Value("${gemini.model:gemini-1.5-flash}")` |
| `httpClient` | `HttpClient` | 30-second connect timeout |
| `objectMapper` | `ObjectMapper` | — |

**Method 1: `analyzeFullTranscript(fullText, chunks, minDuration, maxDuration, topN, acceptedWindows)`**

Returns `List<Map<String, Object>>` — array of clip objects.

```
1. Validate apiKey (throw IllegalStateException if empty)
2. buildPrompt():
   - Role: "viral content expert"
   - Full transcript text
   - Constraints: topN, duration bounds, standalone segments, no mid-sentence cuts
   - POV requirements: pov_en (5-6 words max 7), pov_hi (Hindi Devanagari)
   - Exclusion rule: if acceptedWindows non-empty, include them as JSON to avoid
   - Chunks JSON
3. Build JSON schema for structured output:
   Properties: start (NUMBER), end (NUMBER), score (INTEGER), reasoning (STRING),
               pov_en (STRING), pov_hi (STRING), text (STRING)
   Schema: ARRAY of OBJECT with all required
4. Set generationConfig: responseMimeType="application/json", responseSchema=schema
5. POST to https://generativelanguage.googleapis.com/v1beta/models/{modelName}:generateContent?key={apiKey}
   - Content-Type: application/json
   - 3-minute timeout
6. Parse response: candidates[0].content.parts[0].text
7. Strip markdown fencing (```json ... ```)
8. Deserialize to List<Map>
```

**Method 2: `transcribeAudio(File wavFile)` — Gemini-based transcription**

```
1. Validate apiKey
2. uploadFile(wavFile, "audio/wav") → fileUri  (two-phase resumable upload)
3. Build prompt: request timestamped transcription
4. Build schema: ARRAY of OBJECT with start (NUMBER), end (NUMBER), text (STRING)
5. POST to .../gemini-1.5-flash:generateContent  ⚠ HARDCODED MODEL (ignores this.modelName)
   - 5-minute timeout
6. FINALLY: DELETE the uploaded file from Gemini Files storage (best-effort cleanup)
7. Parse response same as analyzeFullTranscript
```

**uploadFile(file, mimeType) — Two-phase resumable upload:**

```
Phase 1 (Init):
  POST https://generativelanguage.googleapis.com/upload/v1beta/files?key={apiKey}
  Headers: X-Goog-Upload-Protocol: resumable, X-Goog-Upload-Command: start
  Body: {file: {displayName: filename}}
  → Extract X-Goog-Upload-URL from response header

Phase 2 (Upload):
  PUT to upload URL with file bytes
  Headers: X-Goog-Upload-Offset: 0, X-Goog-Upload-Command: finalize
  10-minute timeout
  → Extract file.uri from response JSON
```

**Known Bug:** `transcribeAudio()` hardcodes `gemini-1.5-flash` at line ~213 instead of using `this.modelName`.

---

#### `YouTubeDownloader.java` — yt-dlp Wrapper
**Path:** `src/main/java/com/julius/clipper/service/YouTubeDownloader.java` (9587 bytes)

`@Service`. Wraps yt-dlp subprocess calls for metadata probing, audio download, and video download.

**Fields:**

| Field | Type | Source |
|---|---|---|
| `outputDir` | `String` | `@Value("${clipper.download.dir:data/temp/downloads}")` |
| `cookiesPath` | `String` | `@Value("${youtube.cookies.path:}")` |
| `ytdlpFormat` | `String` | `@Value("${ytdlp.format:bestvideo[height<=720]+bestaudio/best}")` |

**probeVideo(url) — Metadata extraction without download:**

```
Command: yt-dlp --dump-json --no-playlist --force-ipv4 --socket-timeout 30
         --extractor-args youtube:player_client=android,ios,tv
         [--cookiefile {cookiesPath}] {url}

- Separate stdout/stderr consumer threads (non-daemon)
- 60-second timeout → destroyForcibly on timeout
- Returns: deserialized Map from yt-dlp JSON dump
```

**downloadAudio(url, filename):**

```
Command: yt-dlp -f bestaudio/bestaudio*/best
         -o {outputDir}/{filename}.%(ext)s
         --no-playlist --force-ipv4 --socket-timeout 30
         --extractor-args youtube:player_client=android,ios,tv
         [--cookiefile {cookiesPath}] {url}

- 5-minute timeout (300s)
- Post-download: resolveDownloadedFilePath with extensions [.mp3,.m4a,.webm,.opus,.ogg,.wav,.aac]
- Returns: absolute path to downloaded file
```

**downloadVideo(url, filename):**

```
Command: yt-dlp -f {ytdlpFormat} --merge-output-format mp4
         -o {outputDir}/{filename}.%(ext)s
         --no-playlist --force-ipv4 --socket-timeout 30
         --extractor-args youtube:player_client=android,ios,tv
         [--cookiefile {cookiesPath}] {url}

- 10-minute timeout (600s)
- Post-download: resolveDownloadedFilePath with extensions [.mp4]
- Returns: absolute path
```

**resolveDownloadedFilePath(filename, extensions):**

```
1. Check each extension directly: outputDir/{filename}{ext}
2. Fallback: list all files starting with filename prefix
3. Sort: deprioritize .part files
4. Return first match, or throw FileNotFoundException
```

---

#### `MediaConverter.java` — FFmpeg Audio Extraction
**Path:** `src/main/java/com/julius/clipper/service/MediaConverter.java` (4376 bytes)

`@Service`. Converts any media file to 16kHz mono 16-bit PCM WAV (optimized for Whisper).

**convertToWav(inputPath):**

```
Command: ffmpeg -y -nostdin -loglevel error -i {input}
         -ar 16000 -ac 1 -c:a pcm_s16le {output}

- -y: overwrite
- -nostdin: prevents FFmpeg from reading stdin (avoids process hang)
- -ar 16000: 16kHz sample rate (Whisper's native rate)
- -ac 1: mono
- -c:a pcm_s16le: 16-bit signed PCM
- 3-minute timeout (180s)
- Post-validation: output must exist AND be ≥ 1024 bytes
- Returns: output path
```

**Note:** 16kHz mono WAV produces ~115MB/hour of audio. No compression.

---

#### `VideoEditor.java` — FFmpeg Video Cutting
**Path:** `src/main/java/com/julius/clipper/service/VideoEditor.java` (5364 bytes)

`@Service`. Cuts video segments using FFmpeg.

**cutVideo(inputPath, startTime, endTime, outputFilename):**

```
Validation:
- Input file must exist
- endTime > startTime
- If outputFilename is null: auto-generate "{baseName}_cut_{startInt}_{endInt}.mp4"

Command: ffmpeg -y -ss {start} -i {input} -t {duration}
         -c:v libx264 -preset ultrafast -crf 23
         -c:a aac -b:a 128k
         -map 0:v:0 -map 0:a?
         -movflags +faststart {output}

- -ss before -i: fast demuxer-level seek (may lose ~1 keyframe accuracy, but much faster)
- -preset ultrafast: speed-prioritized encoding
- -crf 23: visually acceptable quality
- -map 0:a?: optional audio (won't fail if no audio stream)
- -movflags +faststart: moov atom at start for web streaming
- Dynamic timeout: max(120, min(600, duration * 5)) seconds
- Post-validation: output must exist AND be ≥ 1024 bytes
- Returns: output path
```

---

#### `SegmentMerger.java` — Transcript Segment Merging
**Path:** `src/main/java/com/julius/clipper/service/SegmentMerger.java` (10404 bytes)

`@Service`. Merges raw Whisper transcript segments into coherent chunks.

**Inner Classes:**

| Class | Purpose | Mutability |
|---|---|---|
| `MergeConfig` | Configuration (silenceThreshold=700ms, maxDuration=10s, minDuration=1s, punctuation=".!?") | Immutable |
| `Word` | Single word with timing | Mutable POJO |
| `RawSegment` | Input segment (text, start, end, words) | Mutable POJO |
| `MergedSegment` | Output segment (text, start, end, words) | Immutable |

**merge(rawSegments, includeWords, config) — Core Algorithm:**

```
buffer = {texts: [], words: [], start: -1, end: -1}
results = []

for each segment (skip empty text):
  gap = (segment.start - buffer.end) * 1000  // milliseconds

  shouldBreak = false
  if gap > config.silenceThresholdMs:          shouldBreak = true  // Rule 1: silence gap > 700ms
  elif bufferDuration >= config.maxSegmentDurationS: shouldBreak = true  // Rule 2: exceeded 10s
  elif bufferDuration >= config.minSegmentDurationS  // Rule 3: punctuation after 1s+
    AND lastText endsWith punctuation:         shouldBreak = true

  if shouldBreak AND buffer has content:
    results.add(emitSegment(buffer))
    reset buffer

  buffer.texts.add(segment.text)
  buffer.words.addAll(segment.words)
  if buffer.start < 0: buffer.start = segment.start
  buffer.end = segment.end

// emit remaining buffer
if buffer has content: results.add(emitSegment(buffer))
return results
```

**mergeMaps(listOfMaps, includeWords):**
- Map-based adapter: converts `List<Map>` → `List<RawSegment>` → calls merge() → converts `List<MergedSegment>` → `List<Map>`

---

#### `SlidingWindowSelector.java` — Clip Window Selection
**Path:** `src/main/java/com/julius/clipper/service/SlidingWindowSelector.java` (6496 bytes)

`@Service`. Finds the best contiguous time windows from scored transcript chunks.

**Inner Classes:**

| Class | Fields | Mutability |
|---|---|---|
| `Chunk` | start, end, score, text | Immutable |
| `Window` | start, end, score, text, startIndex, endIndex | Immutable |

**Constructor:** `(minDuration=60.0, maxDuration=300.0)` defaults

**findTopNWindows(chunks, n) — Greedy Non-Overlapping Selection:**

```
usedIndices = new HashSet<Integer>()
results = []

for i in 0..n-1:
  bestWindow = findBestWindowExcluding(chunks, usedIndices)
  if bestWindow is null: break
  for j in bestWindow.startIndex..bestWindow.endIndex:
    usedIndices.add(j)
  results.add(bestWindow)

return results
```

**findBestWindowExcluding(chunks, excludedIndices) — O(n²) Sliding Window:**

```
bestWindow = null
bestScore = -∞

for i in 0..chunks.length-1:
  if i is excluded: continue
  totalDuration = 0, totalScore = 0, chunkCount = 0

  for j in i..chunks.length-1:
    if j is excluded: break  // window broken
    if totalDuration + chunk[j].duration > maxDuration: break

    totalDuration += chunk[j].duration
    totalScore += chunk[j].score
    chunkCount++

    if totalDuration >= minDuration:
      avgScore = totalScore / chunkCount
      if avgScore > bestScore:
        bestScore = avgScore
        bestWindow = Window(chunk[i].start, chunk[j].end, avgScore, joinedText, i, j)

return bestWindow
```

**Scoring uses average-per-chunk, not total.** This favors consistently high-scoring windows over windows with a mix of high and low scores.

---

#### `DistributedLockManager.java` — Redis Distributed Locking
**Path:** `src/main/java/com/julius/clipper/service/DistributedLockManager.java` (2441 bytes)

`@Service`. Provides Redis-based distributed locks.

**Fields:**

| Field | Type | Notes |
|---|---|---|
| `redisTemplate` | `StringRedisTemplate` | Constructor-injected |
| `unlockScript` | `RedisScript<Long>` | Compiled Lua script |

**Lua unlock script (compiled at construction time):**

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
else
  return 0
end
```

This is the standard Redis "check-and-delete" pattern — only the lock owner can release.

**acquireLock(lockKey, ownerId, ttlSeconds) → boolean:**

```
try:
  return redisTemplate.opsForValue().setIfAbsent(lockKey, ownerId, Duration.ofSeconds(ttl))
  // Atomic SET NX with expiry
catch Exception:
  log.error(...)
  return false  // fail-open for acquisition → safe (caller won't proceed)
```

**releaseLock(lockKey, ownerId) → boolean:**

```
try:
  result = redisTemplate.execute(unlockScript, [lockKey], ownerId)  // Lua script, atomic
  return result > 0
catch Exception:
  log.error(...)
  return false
```

---

### 4.6 Controller Layer

#### `JobController.java` — REST API
**Path:** `src/main/java/com/julius/clipper/controller/JobController.java` (6747 bytes)

`@RestController`, `@RequestMapping("/api/jobs")`. The sole HTTP entry point.

**Dependencies:** `JobRepository`, `JobClipRepository`, `QueueProvider`, `RedisMessageListenerContainer`

**Endpoints:**

| Method | Path | Handler | Returns |
|---|---|---|---|
| `POST` | `/api/jobs` | `submitJob` | 202 Accepted with `{jobId}` |
| `GET` | `/api/jobs/{jobId}` | `getJob` | 200 with Job entity or 404 |
| `GET` | `/api/jobs/{jobId}/clips` | `getJobClips` | 200 with `List<JobClip>` |
| `GET` | `/api/jobs/{jobId}/stream` | `streamJobEvents` | SSE stream |

**POST /api/jobs — submitJob:**

```
1. Generate jobId = UUID
2. Resolve userId from X-User-Id header (or random UUID)
3. Build Job: status=PENDING, config=JobConfig, correlationId="correlation-"+UUID[:8]
4. Save Job
5. Build task payload: {job_id, user_id, url, count, copy_language (default "en"),
                        min_duration (default 30.0), max_duration (default 900.0)}
6. Create Task: type=DOWNLOAD, status=PENDING
7. queueProvider.push(task)
8. Return 202 {jobId: uuid}
```

**GET /api/jobs/{jobId}/stream — SSE:**

```
1. Create SseEmitter with 30-minute timeout (1,800,000ms)
2. Subscribe to Redis pub/sub channel "seone:job:{jobId}:events"
3. On each message: forward as SSE event named "progress"
4. Register cleanup (listener removal) on onCompletion/onTimeout/onError
5. Send initial "subscribed" event
6. Return emitter
```

---

### 4.7 Configuration

#### `QueueConfig.java` — Queue Strategy Selection
**Path:** `src/main/java/com/julius/clipper/config/QueueConfig.java` (746 bytes)

`@Configuration`. Selects queue implementation based on `clipper.queue.type` property.

```java
@Bean @Primary
public QueueProvider queueProvider(RedisQueue redisQueue, DbQueue dbQueue) {
    if ("redis".equalsIgnoreCase(queueType)) return redisQueue;
    return dbQueue;  // default
}
```

---

#### `RedisConfig.java` — Redis Pub/Sub Container
**Path:** `src/main/java/com/julius/clipper/config/RedisConfig.java` (649 bytes)

`@Configuration`. Creates the `RedisMessageListenerContainer` bean used by `JobController` for dynamic SSE pub/sub subscriptions.

```java
@Bean
public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    return container;  // No static subscriptions — added dynamically at runtime
}
```

---

#### `WebConfig.java` — Static Resource Mapping
**Path:** `src/main/java/com/julius/clipper/config/WebConfig.java` (661 bytes)

`@Configuration`, implements `WebMvcConfigurer`.

```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/data/**")
            .addResourceLocations("file:" + System.getProperty("user.dir") + "/data/");
}
```

This exposes the `data/` directory via HTTP, serving rendered clips at URLs like `http://localhost:8080/data/jobs/{jobId}/clips/{filename}`.

---

#### `application.properties` (Default Profile)

```properties
spring.application.name=clipper
spring.datasource.url=jdbc:h2:mem:clipperdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.data.redis.host=localhost
spring.data.redis.port=6379
clipper.queue.type=db
logging.level.com.julius.clipper=DEBUG
spring.main.banner-mode=off
```

- H2 in-memory with PostgreSQL compatibility
- DB queue (not Redis) for testing
- No Gemini API key, no file paths

---

#### `application-live.properties` (Live Profile)

```properties
spring.datasource.url=jdbc:h2:mem:clipperdb_live;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
# ... still H2, not real PostgreSQL
clipper.queue.type=redis
google.api.key=${GOOGLE_API_KEY:YOUR_GEMINI_API_KEY}
gemini.model=gemini-1.5-flash
youtube.cookies.path=${YOUTUBE_COOKIES_PATH:}
ytdlp.format=bestvideo[height<=720]+bestaudio/best
clipper.python.path=venv/Scripts/python.exe
# ... all directory paths under data/
```

**Notable:** Even the "live" profile uses H2 in-memory, not PostgreSQL. The `SKIP LOCKED` hint in `TaskRepository` is designed for PostgreSQL but silently ignored by H2.

---

#### `logback-spring.xml`

Structured JSON logging via Logstash encoder:
- Console appender with `LogstashEncoder` (JSON objects, not plain text)
- Root level: INFO
- MDC and context included

---

### 4.8 Repositories

All extend `JpaRepository<Entity, String>` (String PKs).

#### `ClipRepository` — No custom methods. Default CRUD only.

#### `JobClipRepository`

| Method | Return | Purpose |
|---|---|---|
| `findByJobId(String)` | `List<JobClip>` | All clips for a job |
| `countByJobId(String)` | `long` | Count clips for completion check |
| `findByJobIdAndClipIndex(String, int)` | `Optional<JobClip>` | Idempotency guard |
| `findByJobIdAndFilename(String, String)` | `Optional<JobClip>` | Alt lookup |

#### `JobRepository`

| Method | Return | Purpose |
|---|---|---|
| `findByIdempotencyKey(String)` | `Optional<Job>` | Duplicate submission check |
| `countByUserIdAndStatusIn(String, Collection<JobDBStatus>)` | `long` | Rate limiting |
| `countByUserIdAndStatus(String, JobDBStatus)` | `long` | Status-specific count |

#### `JobStepRepository`

| Method | Return | Purpose |
|---|---|---|
| `findByJobId(String)` | `List<JobStep>` | All steps for timeline |
| `findByJobIdAndStepType(String, String)` | `Optional<JobStep>` | Find/create pattern |

#### `TaskRepository` — The Critical Query

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
@Query("SELECT t FROM Task t WHERE t.type = :type AND t.status = :status ORDER BY t.createdAt ASC")
List<Task> findFirstForUpdateSkipLocked(TaskType type, TaskStatus status, Pageable pageable);
```

This is the competing-consumer pattern. `-2` maps to PostgreSQL `SKIP LOCKED`. Workers atomically claim tasks without blocking each other.

#### `UserRepository`

| Method | Return | Purpose |
|---|---|---|
| `findByEmail(String)` | `Optional<User>` | User lookup |

#### `UxFactRepository`

| Method | Return | Purpose |
|---|---|---|
| `findBySlotAndLanguageAndAudienceScopeAndEnabled(...)` | `List<UxFact>` | Dynamic UI content |

---

### 4.9 Actuator & Health

#### `GpuHealthIndicator.java`
**Path:** `src/main/java/com/julius/clipper/actuator/GpuHealthIndicator.java` (3133 bytes)

`@Component`, implements `HealthIndicator`. Available at `/actuator/health` under key `gpu`.

```
Execute: nvidia-smi --query-gpu=temperature.gpu,utilization.gpu,utilization.memory,
                                  memory.total,memory.used
         --format=csv,noheader,nounits
Timeout: 5 seconds

Success (exit 0, ≥5 CSV tokens):
  → Health.UP with temperature, utilization, memory stats

Driver error (non-zero exit):
  → Health.UP with gpu.status = "DRIVER_ERROR"

nvidia-smi not found:
  → Health.UP with gpu.status = "NO_GPU_OR_DRIVER_NOT_FOUND"
```

**Always returns Health.UP.** GPU absence is informational, not degrading. This is intentional for CPU-only environments.

---

### 4.10 Frontend

#### `index.html` — Single-Page Application
**Path:** `src/main/resources/static/index.html` (8863 bytes)

Dark-themed "Production Control Panel" with two-column grid layout.

**Structure:**
1. **Background effects:** 3 `.glow-blob` divs (purple, magenta, cyan radial gradients)
2. **Sticky header:** "JULIUS AI CLIPPER" + system status pill ("Cluster Live" with pulsing dot)
3. **Main grid (380px sidebar | 1fr content):**
   - **Left sidebar:**
     - Job Configuration Card (`#jobForm`): YouTube URL input, Max Clips (1-10), POV Language (en/hi), Template Reference (default-dev/vertical-split/cinematic-portrait), Min/Max duration sliders, "Initialize Pipeline" button
     - Pipeline Status Tracker: 6 step nodes with Lucide icons (Download Audio → Transcribe → Download Video → Gemini Analysis → Smart Render → Job Completed)
   - **Right content:**
     - Real-time Pipeline Logs Console (`#logConsole`): Terminal-style SSE log viewer
     - Clips Grid Panel (`#clipsPanel`): Generated video cards with players
4. **Footer:** Tech stack mention

**External deps:** Google Fonts (Outfit + JetBrains Mono), Lucide Icons (unpkg CDN)

---

#### `styles.css` — Visual Design System
**Path:** `src/main/resources/static/css/styles.css` (15427 bytes)

**CSS Variables:**
- Backgrounds: `#03020d`, `#010006` (near-black)
- Card backgrounds: semi-transparent with `backdrop-filter: blur(12px)` (glassmorphism)
- Text: 3-tier hierarchy (`#f8fafc`, `#94a3b8`, `#64748b`)
- Accents: Purple `#8b5cf6`, Magenta `#ec4899`, Cyan `#06b6d4`, Green `#10b981`, Yellow `#f59e0b`
- Gradient: 135deg purple→magenta
- Fonts: Outfit (UI), JetBrains Mono (terminal)

**Pipeline tracker CSS states:**
- `.node-item.active` → Cyan glow with ring animation
- `.node-item.completed` → Green with checkmark
- `.node-item.failed` → Magenta/red

**Animations:** `spin` (loading), `pulseGlow` (logo), `indicatorPulse` (status dot), `fadeInRow` (terminal lines)

**Responsive:** Collapses to single column at 900px breakpoint.

---

#### `app.js` — Client-Side Logic
**Path:** `src/main/resources/static/js/app.js` (11221 bytes)

**State:** `eventSource` (SSE connection), `currentJobId`

**Core Flow:**

```
1. Form submit → POST /api/jobs
   - Payload: {url, count, min_duration, max_duration, template_ref, copy_language, language_mode:"mixed"}
   - Header: X-User-Id: "6b10e02b-..." (hardcoded dev user)
   - On success: connectSSE(jobId)

2. connectSSE(jobId):
   - Close existing EventSource
   - Open new EventSource to /api/jobs/{jobId}/stream
   - Listen for "subscribed" event → update badge to "Connected" (green)
   - Listen for "progress" event → handlePipelineEvent(data)
   - onerror → badge "Disconnected", close connection

3. handlePipelineEvent(event):
   - step_started → add .active class to tracker node
   - step_completed → remove .active, add .completed
   - progress_update → add .active
   - job_completed → mark ALL nodes .completed, loadJobClips(jobId), close SSE
   - job_failed → mark failed node .failed, close SSE

4. loadJobClips(jobId):
   - GET /api/jobs/{jobId}/clips
   - renderClips(): create video player cards with:
     - <video> src="/data/jobs/{jobId}/clips/{filename}"
     - Duration badge, viral score badge (default 95)
     - Download link
```

**Hardcoded dev user ID:** `6b10e02b-ec31-48e5-bf0f-85fd02ad4fb9` — sent with every request.

---

### 4.11 Python Bridge

#### `transcribe_bridge.py` — Whisper Transcription
**Path:** `scripts/transcribe_bridge.py` (3457 bytes)

**CLI Interface:**
- `--audio` (required): Path to 16kHz WAV file
- `--output` (required): Path to write JSON output

**Execution:**

```python
1. Hardware detection:
   device = "cuda" if torch.cuda.is_available() else "cpu"
   compute = "int8" if cpu else "int8_float16" (mixed precision)

2. Model loading (3-tier fallback):
   try:    WhisperModel("large-v3-turbo", device, compute)
   except ValueError:  WhisperModel("deepdml/faster-whisper-large-v3-turbo-ct2", device, compute)
   except Exception:   WhisperModel("deepdml/...", "cpu", "int8")  # final CPU fallback

3. Transcription:
   segments, info = model.transcribe(audio_path, beam_size=5, word_timestamps=False)

4. Output:
   result = [{start: round(s.start,3), end: round(s.end,3), text: s.text.strip()}, ...]
   write JSON to output file (ensure_ascii=False, indent=2)
   print progress to stdout (read by Java)
```

**Key:** Uses `faster-whisper` (CTranslate2 backend), NOT OpenAI's original Whisper. Model: `large-v3-turbo` (74M params). CUDA auto-detection for GPU acceleration. Timestamps rounded to millisecond precision.

---

### 4.12 Tests

#### `ClipperPipelineTest.java` — Integration Test
**Path:** `src/test/java/com/julius/clipper/ClipperPipelineTest.java` (6794 bytes)

`@SpringBootTest` with `clipper.queue.type=db`. `@ActiveProfiles("test")`. Mocks `RedisConnectionFactory` and `StringRedisTemplate`.

**Tests:**

| Test | What it validates |
|---|---|
| `testEndToEndPipelineExecution()` | Full pipeline from DOWNLOAD task through to COMPLETED status and 1 clip. Polls job status for up to 6 seconds. |
| `testSegmentMerger()` | 3 segments with gaps → SegmentMerger produces 3 merged segments (no merging across gaps) |
| `testSlidingWindowSelector()` | 5 chunks scored [80,95,90,50,40] → best 60s window is chunks 1-3 (avg ~88.3) |

The end-to-end test configures Redis mocks to make the join barrier immediately satisfied (increment returns 1, add returns 1, size returns 2).

#### `ClipperSmokeTest.java` — Live Smoke Test
**Path:** `src/test/java/com/julius/clipper/ClipperSmokeTest.java` (4224 bytes)

`@SpringBootTest(webEnvironment = RANDOM_PORT)`. `@ActiveProfiles("live")`. **No mocks — real external services.**

**Test:** `runLiveVideoEndToEndPipeline()`
- Submits a job via REST API with Rick Astley URL
- Polls every 5 seconds for up to 8 minutes
- Expects COMPLETED status and ≥1 clip
- Requires: Redis, yt-dlp, FFmpeg, Python venv with Whisper, Gemini API key

---

### 4.13 Build & Infrastructure

#### `pom.xml`

```
Parent: spring-boot-starter-parent:3.3.1
Java: 21
Dependencies:
  - spring-boot-starter-data-jpa
  - spring-boot-starter-data-redis
  - spring-boot-starter-web
  - spring-boot-starter-validation
  - spring-boot-starter-actuator
  - logstash-logback-encoder:7.4
  - postgresql (runtime)
  - h2 (runtime)
  - lombok:1.18.46 (optional)
  - jackson-databind
  - jackson-datatype-jsr310
  - spring-boot-starter-test (test)
Plugins:
  - maven-compiler-plugin:3.13.0 (source/target 21, Lombok annotation processor)
  - spring-boot-maven-plugin (excludes Lombok from fat JAR)
```

#### `docker-compose.yml`

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: clipper-redis
    ports: ["6379:6379"]
    volumes: [clipper_redis_data:/data]
    command: redis-server --appendonly yes
    restart: always
```

Only Redis. No PostgreSQL container (H2 is used). No application container.

#### `requirements.txt`

```
faster-whisper==1.0.3
torch
requests
```

Python dependencies for `transcribe_bridge.py`.

#### `data/` Directory Structure

```
data/
├── temp/
│   ├── downloads/    → Raw yt-dlp downloads
│   ├── converted/    → FFmpeg WAV conversions
│   └── fragments/    → FFmpeg video cuts
├── library/
│   ├── videos/       → Permanent video library
│   ├── audios/       → Permanent audio library (WAV)
│   └── cache/        → Transcript and analysis JSON caches
└── jobs/
    └── {jobId}/
        └── clips/    → Final rendered output clips
```

---

## 5. SYSTEM ORCHESTRATION & COMPONENT BOUNDARIES

### Component Boundary Map

```
┌──────────────────────────────────────────────────────────────────────┐
│                         HTTP LAYER                                   │
│  JobController (REST API + SSE)                                     │
│    ↓ POST /api/jobs                    ↑ SSE events                 │
│    ↓                                   │                            │
│  ┌─────────────┐              ┌────────┴──────┐                     │
│  │ QueueProvider│              │EventPublisher │                     │
│  │(push/pop)    │              │(Redis Pub/Sub)│                     │
│  └──────┬───────┘              └───────────────┘                     │
│         │                              ↑                            │
├─────────┼──────────────────────────────┼────────────────────────────┤
│         │         PIPELINE ENGINE      │                            │
│  ┌──────┴───────┐              ┌───────┴──────┐                     │
│  │ WorkerRunner │───executes──→│  Orchestrator │                    │
│  │(polling loop)│   then asks  │ (DAG routing) │                    │
│  │(semaphores)  │  for next    │ (job lifecycle)│                    │
│  │(heartbeat)   │   tasks      │ (fork/join)    │                    │
│  └──────┬───────┘              └───────────────┘                     │
│         │                                                           │
│  ┌──────┴───────────────────────────────────────────┐               │
│  │              WORKER IMPLEMENTATIONS               │               │
│  │  DownloadWorker → IngestWorker → TranscribeWorker │               │
│  │  → AnalyzeWorker → SmartRenderWorker              │               │
│  └──────┬──────────────┬───────────────┬─────────────┘               │
│         │              │               │                            │
├─────────┼──────────────┼───────────────┼────────────────────────────┤
│         │     SERVICE LAYER            │                            │
│  ┌──────┴──────┐ ┌─────┴─────┐ ┌──────┴──────┐                     │
│  │YouTubeDown- │ │GeminiSvc  │ │MediaConvert │                     │
│  │  loader     │ │(API call) │ │VideoEditor  │                     │
│  │(yt-dlp)     │ │           │ │(FFmpeg)     │                     │
│  └─────────────┘ └───────────┘ └─────────────┘                     │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                      DATA LAYER                                     │
│  ┌──────────┐  ┌──────────┐  ┌────────┐  ┌───────┐                 │
│  │ H2/PG DB │  │  Redis   │  │  Files │  │Gemini │                 │
│  │(JPA)     │  │(Queue,   │  │(data/) │  │ API   │                 │
│  │          │  │Lock,SSE) │  │        │  │       │                 │
│  └──────────┘  └──────────┘  └────────┘  └───────┘                 │
└──────────────────────────────────────────────────────────────────────┘
```

### Synchronization Mechanisms

| Mechanism | Technology | Purpose |
|---|---|---|
| **Task Queue** | Redis LIST (RPOPLPUSH) or DB (SELECT FOR UPDATE SKIP LOCKED) | Competing-consumer task dispatch |
| **Fork-Join Barrier** | Redis SET (SADD + SCARD) | Wait for both TRANSCRIBE and DOWNLOAD_VIDEO branches |
| **Distributed Lock** | Redis SET NX + Lua script | Prevent concurrent downloads of the same video |
| **Semaphores** | JVM `java.util.concurrent.Semaphore` | In-process resource pool limits (IO/CPU/GPU) |
| **Heartbeat** | ScheduledExecutorService → Redis HSET | Liveness signaling for running tasks |
| **Event Delivery** | Redis Streams + Pub/Sub → SSE | Real-time progress updates to browser |

### Decoupling Strategies

1. **Queue Abstraction:** `QueueProvider` interface decouples pipeline engine from queue technology. `RedisQueue` and `DbQueue` are interchangeable via `clipper.queue.type`.

2. **Worker Interface:** `Worker` functional interface decouples WorkerRunner from step implementations. Workers are looked up by bean name, not injected by type.

3. **Event Publishing:** `EventPublisher` is fire-and-forget. Pipeline execution is not blocked by event delivery failures. All exceptions are caught and logged.

4. **File-Based Inter-Step Communication:** Workers write to filesystem and pass paths via task payload maps. No in-memory data sharing between pipeline steps.

5. **Subprocess Isolation:** Whisper, yt-dlp, and FFmpeg run as separate OS processes. JVM crash won't corrupt their state. Timeout + destroy provides cleanup.

---

## 6. UNDER-THE-HOOD MECHANICS & REALITY

### Hard Tradeoffs

| Decision | What Was Gained | What Was Sacrificed |
|---|---|---|
| **H2 instead of real PostgreSQL** | Zero-config development, instant startup, no infra dependency | `SKIP LOCKED` silently ignored by H2 (no real competing-consumer safety), no production durability, data lost on restart |
| **FFmpeg `-preset ultrafast`** | Fast video encoding (2-5x faster than `medium`) | Larger file sizes (~50% bigger), slightly lower visual quality |
| **FFmpeg `-ss` before `-i`** | Fast demuxer-level seek | May lose ~1 keyframe of accuracy at cut boundaries |
| **Two-pass render (cut then re-encode)** | Clean separation of temporal and spatial operations | Each clip is re-encoded twice — double the compute |
| **Generic `Map<String,Object>` payloads** | Maximum flexibility, no per-step DTO classes | Zero compile-time type safety, runtime `ClassCastException` risk |
| **Python subprocess for Whisper** | Leverage Python ML ecosystem directly | Process startup overhead, no streaming progress, potential OOM without JVM awareness |
| **Single GPU semaphore (1 permit)** | Prevents GPU OOM from concurrent Whisper + FFmpeg | Only one GPU task at a time — severe bottleneck for multi-clip renders |
| **Polling-based queue consumption** | Simple implementation, works with both Redis and DB | 1-second idle sleep latency, wasted CPU cycles when idle |
| **File-based caching** | Simple, no additional infrastructure | No TTL, no eviction, grows unbounded, no cache invalidation |
| **API key as URL query parameter** | Simple, works with Java HttpClient | API key visible in logs, network traces, and server access logs |

### Failure Modes & Edge Cases

#### Critical Failures (System Stops Working)

| Failure | Impact | Detection | Recovery |
|---|---|---|---|
| **Redis down** (live profile) | All task enqueue/dequeue fails, SSE events stop, distributed locks fail | Spring auto-config connection failure | Switch to `clipper.queue.type=db` (loses in-flight Redis tasks) |
| **Gemini API key invalid** | AnalyzeWorker throws `IllegalStateException`, job fails | Error in job status | Set correct `GOOGLE_API_KEY` env var |
| **yt-dlp not on PATH** | DownloadWorker subprocess fails with OS error | `IOException` in process start | Install yt-dlp |
| **FFmpeg not on PATH** | MediaConverter/VideoEditor subprocess fails | `IOException` | Install FFmpeg |
| **Python venv missing** | TranscribeWorker subprocess fails | Non-zero exit code | Create venv, install requirements |
| **Disk full** | File writes fail across all workers | `IOException` | Clear `data/temp/` directory |

#### Silent Failures (System Appears to Work But Doesn't)

| Failure | Impact | Why It's Silent |
|---|---|---|
| **H2 ignores SKIP LOCKED** | Concurrent DbQueue.pop() can dequeue the same task twice | H2 executes the query but ignores the lock hint — no error |
| **INGEST path hangs at join** | INGEST → TRANSCRIBE → ANALYZE hits join barrier but DOWNLOAD_VIDEO never runs. Set size never reaches 2. Job stays in PROCESSING forever. | No timeout on the join barrier — Orchestrator just returns empty task list |
| **TranscribeWorker has no timeout** | Python Whisper subprocess blocks indefinitely if it hangs | `process.waitFor()` is called without a timeout argument |
| **DLQ errors silently swallowed** | RedisQueue.fail() catches and ignores errors during DLQ creation | Empty catch block |
| **Gemini transcription uses wrong model** | `transcribeAudio()` hardcodes `gemini-1.5-flash` ignoring `gemini.model` config | Bug at line ~213 — hardcoded string in URL |
| **markJobFailed uses findAll()** | Cancellation of outstanding tasks scans the entire task table | Works fine with few tasks, becomes O(n) performance issue at scale |
| **SmartRenderWorker hardcodes localhost** | `url` field in JobClip is always `http://localhost:8080/...` | Works in development, breaks in any other deployment |
| **Gemini API key fallback** | `${GOOGLE_API_KEY:YOUR_GEMINI_API_KEY}` falls back to literal string "YOUR_GEMINI_API_KEY" | Looks like a real key, causes opaque 403 errors |

#### Race Conditions

| Race | Trigger | Severity |
|---|---|---|
| **Double task dequeue (H2)** | Two WorkerRunner threads call DbQueue.pop() simultaneously | Medium — same task executed twice, produces duplicate results |
| **Join barrier double-fire** | Two branches complete at the exact same instant, both see SCARD==2 | Low — SADD is atomic, but both threads could read size==2 before the other's SADD. Would create 2× SMART_RENDER tasks. |
| **Zombie task resurrection** | Task completes after job is already failed. Zombie guard catches this. | Low — handled by zombie guard in Orchestrator.getNextTasks() |

### Recovery Strategies

| Boundary | Recovery Mechanism | Limitation |
|---|---|---|
| **Task execution failure** | RedisQueue.fail() moves to DLQ. DbQueue.fail() marks FAILED in DB. Orchestrator.markJobFailed() cascades to cancel all pending tasks. | No automatic retry. Tasks go to DLQ permanently. |
| **Worker subprocess crash** | Process.destroy() on timeout. JVM catches exception, marks task failed. | No cleanup of partially-written output files |
| **Redis connection loss** | Spring auto-reconnect. QueueProvider calls wrapped in try-catch. | In-flight Redis Streams/SET state may be corrupted |
| **Distributed lock expiry** | 600-second TTL auto-releases stale locks | If download takes >10 minutes, lock expires while still downloading — another worker could start the same download |
| **Heartbeat timeout** | Heartbeat every 30s. No reaper implemented yet — heartbeat is one-way signal. | Nothing currently reads heartbeats to detect dead workers |
| **JVM restart** | H2 in-memory DB is lost entirely. Redis retains queue state. | Jobs in PROCESSING state become orphans. No startup recovery scan. |

### Micrometer Metrics

| Metric | Type | Tags | Source |
|---|---|---|---|
| `clipper.pipeline.active.workers` | Gauge | — | WorkerRunner (activeWorkersCount) |
| `clipper.task.execution.duration` | Timer | `type` + percentiles p50/p90/p95/p99 | WorkerRunner finally block |
| `clipper.tasks.processed.total` | Counter | `type`, `status` (SUCCESS/FAILED) | WorkerRunner finally block |
| `clipper.jobs.processed.total` | Counter | `status` (SUCCESS/FAILED) | Orchestrator (registerClipOutput / markJobFailed) |

### Redis Key Namespace

All keys use the `seone:` prefix (likely a project codename or previous name).

| Key Pattern | Type | TTL | Purpose |
|---|---|---|---|
| `seone:task:{taskId}` | HASH | 7 days | Serialized task data |
| `seone:{userId}:queue:{TYPE}` | LIST | — | Per-tenant pending queue |
| `seone:{userId}:processing:{TYPE}` | LIST | — | Per-tenant in-flight |
| `seone:active_tenants` | SET | — | All users with queued work |
| `seone:pending_signal:{TYPE}` | LIST | — | KEDA autoscaler signal |
| `seone:processing_signal:{TYPE}` | LIST | — | KEDA autoscaler signal |
| `seone:{userId}:dlq` | LIST | — | Dead letter queue (max 10k) |
| `seone:{userId}:job:{jobId}:events` | STREAM | ~20k entries | Pipeline event stream |
| `seone:{userId}:job:{jobId}:seq` | STRING | — | Monotonic event sequence counter |
| `seone:job:{jobId}:events` | CHANNEL | — | Pub/sub for SSE delivery |
| `seone:job_events` | CHANNEL | — | Global event pub/sub |
| `seone:{userId}:join:{jobId}:smart_render_prep` | SET | — | Fork-join barrier |
| `seone:job:{jobId}:video_key` | STRING | 1 hour | Cached video path for join |
| `seone:job:{jobId}:analysis_results_cache` | STRING | 1 hour | Cached analysis path for join |
| `seone:lock:download:{clipId}` | STRING | 600s | Distributed download lock |

---

## END OF MANIFEST

**File count:** 45 source files analyzed (33 Java, 3 config, 1 Python, 3 frontend, 1 XML, 1 YAML, 1 properties, 2 test)

**Total codebase size:** ~170KB of source code (excluding generated target/ and venv/)

**Architecture pattern:** Event-driven pipeline with fork-join concurrency, multi-tenant task queuing, subprocess-based media processing, and LLM-powered content analysis.
