# Telemetry and Observability Guide

Julius uses standard Spring Boot Actuator, Micrometer, and OpenTelemetry to collect and export observability metrics.

## Naming Conventions
All custom metrics are structured using hierarchical namespace formats prefixed by `clipper.`:
- `<subsystem>.<metric_name>`
- Tags are lowercase names representing dimensions, keeping label cardinality under control.

---

## Available Metrics

### 1. API Subsystem (`ApiMetrics`)
*   **Metric Name:** `clipper.api.requests`
*   **Type:** Timer
*   **Unit:** Seconds
*   **Tags:**
    *   `method`: HTTP Verb (e.g. `POST`, `GET`)
    *   `uri`: Endpoint pattern path (e.g. `/api/jobs`)
    *   `status`: HTTP Status code (e.g. `200`, `202`, `500`)
*   **Purpose:** Measure API transaction durations, transaction frequencies, and response rates (RED method).

### 2. Worker Subsystem (`WorkerMetrics`)
*   **Metric Name:** `clipper.worker.task`
*   **Type:** Timer
*   **Unit:** Seconds
*   **Tags:**
    *   `task_type`: TaskType enum name (e.g., `DOWNLOAD`, `TRANSCRIBE`, `SMART_RENDER`)
    *   `status`: Completion outcome status (`success`, `failed`)
*   **Purpose:** Track background task processing execution latencies and fail frequencies.

### 3. Queue Subsystem (`QueueMetrics` & direct instrumentations)
*   **Metric Name:** `clipper.queue.backlog`
*   **Type:** Gauge
*   **Unit:** Counts
*   **Tags:**
    *   `queue_name`: TaskType queue type name
*   **Purpose:** Tracks backlog size of pending items in queues (USE saturation metric).

*   **Metric Name:** `clipper.queue.operations`
*   **Type:** Counter
*   **Unit:** Counts
*   **Tags:**
    *   `operation`: Queue actions (`push`, `pop`, `complete`, `fail`)
    *   `task_type`: Task type name
*   **Purpose:** Tracks message transfer/throughput rates.

### 4. Storage Subsystem (`StorageMetrics`)
*   **Metric Name:** `clipper.storage.bytes`
*   **Type:** Counter
*   **Unit:** Bytes
*   **Tags:**
    *   `operation`: Transfer action (`upload`, `download`)
    *   `provider`: Underlying storage type (`local`, `gcs`)
*   **Purpose:** Track network data volume transferred to/from cloud media storage.

*   **Metric Name:** `clipper.storage.duration`
*   **Type:** Timer
*   **Unit:** Seconds
*   **Tags:**
    *   `operation`: Operation name (`upload`, `download`, `exists`, `delete`)
    *   `provider`: Storage client type (`local`, `gcs`)
*   **Purpose:** Measure storage request latency.

*   **Metric Name:** `clipper.storage.failures`
*   **Type:** Counter
*   **Unit:** Counts
*   **Tags:**
    *   `operation`: Operation name
    *   `provider`: Storage client type
    *   `exception`: Simplename of exception class thrown
*   **Purpose:** Tracks storage interaction error rates.

### 5. AI Subsystem (`AiMetrics`)
*   **Metric Name:** `clipper.ai.whisper.duration`
*   **Type:** Timer
*   **Unit:** Seconds
*   **Tags:**
    *   `model`: Target execution model (e.g. `large-v3-turbo`)
    *   `status`: Exit state status (`success`, `failed`)
*   **Purpose:** Measure Whisper speech-to-text bridge execution durations.

*   **Metric Name:** `clipper.ai.gemini.tokens`
*   **Type:** Counter
*   **Unit:** Counts
*   **Tags:**
    *   `model`: Target LLM model (e.g. `gemini-1.5-flash`)
    *   `type`: Token type classification (`prompt`, `completion`)
*   **Purpose:** Tracks API token consumption to monitor processing costs.

---

## Cardinality Safety Rules
To prevent memory leaks and dashboard performance issues, **never** record high-cardinality values as metric tags/labels:
*   NO `userId` / `user_id`
*   NO `jobId` / `job_id`
*   NO `taskId` / `task_id`
*   NO `correlationId` / `correlation_id`

---

## Prometheus Scraping

Spring Boot exposes a Prometheus-compatible metrics endpoint at:
`http://localhost:8080/actuator/prometheus`

### Prometheus Scraping Configuration
Add the following target details to your `prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'julius-clipper'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']
```

---

## Local Verification
1. Run Julius server.
2. Query `/actuator/prometheus` in your browser or curl:
   ```bash
   curl http://localhost:8080/actuator/prometheus
   ```
3. Locate metric variables prefix `clipper_` to verify their existence and values.
