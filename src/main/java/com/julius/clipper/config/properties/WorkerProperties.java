package com.julius.clipper.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clipper.worker")
public record WorkerProperties(
    @Min(1) int ioConcurrencyLimit,
    @Min(1) int cpuConcurrencyLimit,
    @Min(1) int gpuConcurrencyLimit
) {}
