package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clipper.telemetry")
public record TelemetryProperties(
    @NotBlank String env,
    Otlp otlp
) {
    public record Otlp(
        @NotBlank String endpoint,
        double samplingRatio
    ) {}
}
