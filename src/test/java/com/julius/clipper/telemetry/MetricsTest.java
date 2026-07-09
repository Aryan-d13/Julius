package com.julius.clipper.telemetry;

import com.julius.clipper.pipeline.QueueProvider;
import com.julius.clipper.pipeline.TaskType;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class MetricsTest {

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    public void testApiMetrics() {
        ApiMetrics apiMetrics = new ApiMetrics(meterRegistry);
        Timer.Sample sample = apiMetrics.startRequest();
        
        // Simulate processing
        try {
            TimeUnit.MILLISECONDS.sleep(10);
        } catch (InterruptedException ignored) {}

        apiMetrics.recordRequest(sample, "POST", "/api/jobs", 202);

        Timer timer = meterRegistry.find("clipper.api.requests").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        assertThat(timer.getId().getTag("method")).isEqualTo("POST");
        assertThat(timer.getId().getTag("uri")).isEqualTo("/api/jobs");
        assertThat(timer.getId().getTag("status")).isEqualTo("202");
    }

    @Test
    public void testWorkerMetrics() {
        WorkerMetrics workerMetrics = new WorkerMetrics(meterRegistry);
        Timer.Sample sample = workerMetrics.startTask();
        
        workerMetrics.recordTask(sample, "TRANSCRIBE", "success");

        Timer timer = meterRegistry.find("clipper.worker.task").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("task_type")).isEqualTo("TRANSCRIBE");
        assertThat(timer.getId().getTag("status")).isEqualTo("success");
    }

    @Test
    public void testQueueMetricsAndBacklogGauge() {
        QueueProvider queueProvider = mock(QueueProvider.class);
        when(queueProvider.getQueueDepth(TaskType.DOWNLOAD)).thenReturn(5L);
        when(queueProvider.getQueueDepth(TaskType.TRANSCRIBE)).thenReturn(10L);

        // Register queue metrics configuration
        QueueMetrics queueMetrics = new QueueMetrics(meterRegistry, queueProvider);

        // Verify Gauges are registered and retrieve backlog depth
        Gauge downloadGauge = meterRegistry.find("clipper.queue.backlog")
                .tag("queue_name", "DOWNLOAD")
                .gauge();
        assertThat(downloadGauge).isNotNull();
        assertThat(downloadGauge.value()).isEqualTo(5.0);

        Gauge transcribeGauge = meterRegistry.find("clipper.queue.backlog")
                .tag("queue_name", "TRANSCRIBE")
                .gauge();
        assertThat(transcribeGauge).isNotNull();
        assertThat(transcribeGauge.value()).isEqualTo(10.0);

        // Verify Counter
        queueMetrics.recordOperation("push", "DOWNLOAD");
        Counter counter = meterRegistry.find("clipper.queue.operations").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
        assertThat(counter.getId().getTag("operation")).isEqualTo("push");
        assertThat(counter.getId().getTag("task_type")).isEqualTo("DOWNLOAD");
    }

    @Test
    public void testStorageMetrics() {
        StorageMetrics storageMetrics = new StorageMetrics(meterRegistry);

        // Bytes
        storageMetrics.recordBytes("upload", "gcs", 1024L);
        Counter bytesCounter = meterRegistry.find("clipper.storage.bytes").counter();
        assertThat(bytesCounter).isNotNull();
        assertThat(bytesCounter.count()).isEqualTo(1024.0);
        assertThat(bytesCounter.getId().getTag("operation")).isEqualTo("upload");
        assertThat(bytesCounter.getId().getTag("provider")).isEqualTo("gcs");

        // Failure
        storageMetrics.recordFailure("download", "local", "IOException");
        Counter failCounter = meterRegistry.find("clipper.storage.failures").counter();
        assertThat(failCounter).isNotNull();
        assertThat(failCounter.count()).isEqualTo(1.0);
        assertThat(failCounter.getId().getTag("operation")).isEqualTo("download");
        assertThat(failCounter.getId().getTag("provider")).isEqualTo("local");
        assertThat(failCounter.getId().getTag("exception")).isEqualTo("IOException");

        // Duration pre-calculated
        storageMetrics.recordDuration("exists", "gcs", 50L, TimeUnit.MILLISECONDS);
        Timer timer = meterRegistry.find("clipper.storage.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("operation")).isEqualTo("exists");
        assertThat(timer.getId().getTag("provider")).isEqualTo("gcs");
    }

    @Test
    public void testAiMetrics() {
        AiMetrics aiMetrics = new AiMetrics(meterRegistry);

        // Whisper Duration
        Timer.Sample sample = aiMetrics.startTranscription();
        aiMetrics.recordTranscription(sample, "large-v3-turbo", "success");
        Timer whisperTimer = meterRegistry.find("clipper.ai.whisper.duration").timer();
        assertThat(whisperTimer).isNotNull();
        assertThat(whisperTimer.count()).isEqualTo(1);
        assertThat(whisperTimer.getId().getTag("model")).isEqualTo("large-v3-turbo");
        assertThat(whisperTimer.getId().getTag("status")).isEqualTo("success");

        // Gemini Tokens
        aiMetrics.recordGeminiTokens("gemini-1.5-flash", "prompt", 250L);
        Counter tokenCounter = meterRegistry.find("clipper.ai.gemini.tokens").counter();
        assertThat(tokenCounter).isNotNull();
        assertThat(tokenCounter.count()).isEqualTo(250.0);
        assertThat(tokenCounter.getId().getTag("model")).isEqualTo("gemini-1.5-flash");
        assertThat(tokenCounter.getId().getTag("type")).isEqualTo("prompt");
    }

    @Test
    public void testDuplicateRegistrationProtection() {
        ApiMetrics m1 = new ApiMetrics(meterRegistry);
        ApiMetrics m2 = new ApiMetrics(meterRegistry);

        Timer.Sample s1 = m1.startRequest();
        m1.recordRequest(s1, "GET", "/api/jobs", 200);

        Timer.Sample s2 = m2.startRequest();
        m2.recordRequest(s2, "GET", "/api/jobs", 200);

        // Verify both record into the same underlying meter
        Timer timer = meterRegistry.find("clipper.api.requests").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
    }
}
