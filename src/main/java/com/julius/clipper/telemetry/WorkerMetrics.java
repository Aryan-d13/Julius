package com.julius.clipper.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class WorkerMetrics {

    private final MeterRegistry meterRegistry;

    public WorkerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startTask() {
        return Timer.start(meterRegistry);
    }

    public void recordTask(Timer.Sample sample, String taskType, String status) {
        sample.stop(Timer.builder("clipper.worker.task")
                .description("Worker task execution duration and status")
                .tag("task_type", taskType)
                .tag("status", status)
                .register(meterRegistry));
    }
}
