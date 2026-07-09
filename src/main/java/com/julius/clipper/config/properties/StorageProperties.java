package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clipper.storage")
public record StorageProperties(
    @NotBlank String type,
    Local local,
    Gcs gcs
) {
    public record Local(String root) {}
    public record Gcs(String bucket) {}
}
