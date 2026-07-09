package com.julius.clipper.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class StorageMetrics {

    private final MeterRegistry meterRegistry;

    public StorageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startOperation() {
        return Timer.start(meterRegistry);
    }

    public void recordOperation(Timer.Sample sample, String operation, String provider) {
        sample.stop(Timer.builder("clipper.storage.duration")
                .description("Storage operation duration")
                .tag("operation", operation)
                .tag("provider", provider)
                .register(meterRegistry));
    }

    public void recordDuration(String operation, String provider, long duration, java.util.concurrent.TimeUnit unit) {
        Timer.builder("clipper.storage.duration")
                .description("Storage operation duration")
                .tag("operation", operation)
                .tag("provider", provider)
                .register(meterRegistry)
                .record(duration, unit);
    }

    public void recordBytes(String operation, String provider, long bytes) {
        Counter.builder("clipper.storage.bytes")
                .description("Data bytes transferred")
                .tag("operation", operation)
                .tag("provider", provider)
                .register(meterRegistry)
                .increment(bytes);
    }

    public void recordFailure(String operation, String provider, String exceptionClass) {
        Counter.builder("clipper.storage.failures")
                .description("Storage operations failures total")
                .tag("operation", operation)
                .tag("provider", provider)
                .tag("exception", exceptionClass)
                .register(meterRegistry)
                .increment();
    }
}
