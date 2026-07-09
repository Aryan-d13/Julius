package com.julius.clipper.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ApiMetrics {

    private final MeterRegistry meterRegistry;

    public ApiMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startRequest() {
        return Timer.start(meterRegistry);
    }

    public void recordRequest(Timer.Sample sample, String method, String uri, int status) {
        sample.stop(Timer.builder("clipper.api.requests")
                .description("API requests latency and outcomes")
                .tag("method", method)
                .tag("uri", uri)
                .tag("status", String.valueOf(status))
                .register(meterRegistry));
    }
}
