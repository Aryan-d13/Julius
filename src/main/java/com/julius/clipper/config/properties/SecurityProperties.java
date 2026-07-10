package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clipper.security")
public record SecurityProperties(
    boolean corsEnabled,
    @NotBlank String allowedOrigins,
    @NotNull Jwt jwt
) {
    public record Jwt(
        @NotBlank String secret,
        long accessExpiryMs,
        long refreshExpiryMs
    ) {}
}
