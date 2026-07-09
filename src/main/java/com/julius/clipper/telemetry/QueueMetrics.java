package com.julius.clipper.telemetry;

import com.julius.clipper.pipeline.QueueProvider;
import com.julius.clipper.pipeline.TaskType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueMetrics {

    private final MeterRegistry meterRegistry;
    private final QueueProvider queueProvider;

    public QueueMetrics(MeterRegistry meterRegistry, QueueProvider queueProvider) {
        this.meterRegistry = meterRegistry;
        this.queueProvider = queueProvider;
        registerBacklogGauges();
    }

    private void registerBacklogGauges() {
        for (TaskType type : TaskType.values()) {
            Gauge.builder("clipper.queue.backlog", () -> queueProvider.getQueueDepth(type))
                    .description("The backlog size of pending tasks in the queue")
                    .tag("queue_name", type.name())
                    .register(meterRegistry);
        }
    }

    public void recordOperation(String op, String taskType) {
        Counter.builder("clipper.queue.operations")
                .description("Total number of queue operations")
                .tag("operation", op)
                .tag("task_type", taskType)
                .register(meterRegistry)
                .increment();
    }
}
