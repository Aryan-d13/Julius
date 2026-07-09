package com.julius.clipper.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class AiMetrics {

    private final MeterRegistry meterRegistry;

    public AiMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startTranscription() {
        return Timer.start(meterRegistry);
    }

    public void recordTranscription(Timer.Sample sample, String model, String status) {
        sample.stop(Timer.builder("clipper.ai.whisper.duration")
                .description("Whisper transcription bridge duration")
                .tag("model", model)
                .tag("status", status)
                .register(meterRegistry));
    }

    public void recordGeminiTokens(String model, String type, long count) {
        Counter.builder("clipper.ai.gemini.tokens")
                .description("Gemini API prompt and completion token counts")
                .tag("model", model)
                .tag("type", type)
                .register(meterRegistry)
                .increment(count);
    }
}
