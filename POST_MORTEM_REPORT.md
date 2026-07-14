# POST_MORTEM_REPORT

## Executive Summary
This application is structurally fragile. It mimics a pipeline but lacks the safety guarantees of a commercial-grade orchestration engine. Under production load or hostile conditions (API failures, bad LLM responses, process interruptions), the system will suffer from thread starvation, zombie processes, silent data corruption, and deadlocks. 

---

## 1. Architecture Gaps & Scalability Deficits

### 1.1 No Resiliency for Scale-to-Zero Environments
**Location:** `JobController.java`, `Orchestrator.java`
The system lacks a durable queue worker mechanism with visibility timeouts and Dead Letter Queues (DLQ). State transitions are handled by pushing `Task` objects to a `QueueProvider` (presumably Redis PubSub or simple list), but there is no lease mechanism.
**Vulnerability:** If a container running a `TRANSCRIBE` or `SMART_RENDER` task is shut down or OOM-killed, the task is lost forever. The database will permanently show the job as `PROCESSING` because no watchdog monitors for stalled jobs.

### 1.2 Join Barrier Deadlock
**Location:** `Orchestrator.java:128` (Join barrier logic)
The system uses Redis sets (`seone:userId:join:jobId:smart_render_prep`) to synchronize the parallel execution of `DOWNLOAD_VIDEO` and `ANALYZE`. 
**Vulnerability:** If either the video download fails silently or the LLM analysis crashes before saving to Redis, the join set count will never reach 2. The other successful branch will complete, but the job will remain perpetually hung, waiting for a signal that will never arrive. 

---

## 2. Critical Vulnerabilities

### 2.1 Thread Starvation via Default ForkJoinPool
**Location:** `FFmpegRenderer.java:38`
```java
return CompletableFuture.supplyAsync(() -> { ... process.waitFor(timeoutSeconds, TimeUnit.SECONDS); ... });
```
**Vulnerability:** The render engine wraps a 10-minute blocking OS process (`process.waitFor()`) inside `CompletableFuture.supplyAsync()`. By default, this runs on the JVM's `ForkJoinPool.commonPool()`, which has a thread limit of `(CPU Cores - 1)`. If 10 users submit rendering jobs on a 4-core machine, the common pool is immediately exhausted. All other async operations in the JVM will hang indefinitely, effectively causing a full system lockup without crashing.

### 2.2 Zombie OS Processes & IO Concurrency Faults
**Location:** `FFmpegRenderer.java:107` and `MediaConverter.java:88`
**Vulnerability:** If the Java thread executing `process.waitFor()` is interrupted (e.g., due to an upstream cancellation, timeout, or container shutdown), the `Process` is never cleaned up in a `finally` block. `destroyForcibly()` is only called explicitly if `waitFor` returns false after the timeout. This will leave zombie `ffmpeg` and `yt-dlp` instances draining system memory.
Additionally, the stream consumers append to a `StringBuilder` without a `.join()` before the main thread inspects it, opening up a classic race condition and potential `ConcurrentModificationException`.

---

## 3. The LLM Framework Deficit

### 3.1 Unbounded Timestamp Hallucination
**Location:** `AnalyzeWorker.java:125`
```java
double start = ((Number) clip.get("start")).doubleValue();
double end = ((Number) clip.get("end")).doubleValue();
double duration = end - start;
if (duration < minDuration || duration > maxDuration) { ... }
```
**Vulnerability:** The validation only checks if the *duration* of the clip is within bounds. It does **not** check if the timestamps exceed the actual bounds of the video. If Gemini hallucinates `start: 1000` and `end: 1030` for a 2-minute video, it passes validation. FFmpeg will attempt to slice from a non-existent timestamp, resulting in a silent failure or a 0-byte corrupt file that crashes the job.

### 3.2 Lack of Structural Fallbacks
**Location:** `AnalyzeWorker.java`
The system attempts one retry if Gemini returns fewer clips than expected. However, if the JSON response is completely malformed or violates the schema in a way that Jackson's `ObjectMapper` cannot parse, the `AnalyzeWorker` immediately throws an exception and crashes the job pipeline. There is no fallback prompt, temperature adjustment, or recursive correction loop built into `GeminiService.java`.

---

## 4. Remediation Blueprint

### Priority 1: Thread Isolation (Immediate)
- Refactor `FFmpegRenderer`, `MediaConverter`, and `YouTubeDownloader` to use a dedicated, unbounded or carefully sized `ThreadPoolExecutor` (e.g., `Executors.newCachedThreadPool()`) for blocking OS commands. **Never** block the `ForkJoinPool` with `Process.waitFor()`.

### Priority 2: Process Lifecycle Safety (Immediate)
- Wrap all `ProcessBuilder` executions in a robust `try-finally` block. Ensure `process.destroyForcibly()` is always called if the thread is interrupted or the method exits exceptionally.
- Ensure stream consumer threads are properly `.join()`ed before evaluating process output.

### Priority 3: Hallucination Defense (High)
- Pass the full video duration into `AnalyzeWorker`. Add strict boundary checks: `start < 0` or `end > videoDuration` must instantly invalidate the hallucinated clip.
- Implement a rigid JSON parser with regex fallbacks to extract code blocks from Gemini's output when `ObjectMapper` fails.

### Priority 4: Commercial Job Orchestration (Medium)
- Abandon Redis PubSub for task orchestration. Implement a durable broker (like RabbitMQ, AWS SQS, or Temporal) where tasks require explicit ACKs. If a worker dies, the visibility timeout expires, and another worker picks up the job.
- Implement a watchdog service to detect and fail jobs that have been stuck in `PROCESSING` longer than an hour.
