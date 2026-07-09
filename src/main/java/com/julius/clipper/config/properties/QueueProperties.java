package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clipper.queue")
public record QueueProperties(
    @NotBlank String type,
    Redis redis
) {
    public record Redis(String host, Integer port, String password) {}
}
