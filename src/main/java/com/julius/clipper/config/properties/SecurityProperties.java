package com.julius.clipper.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clipper.security")
public record SecurityProperties(
    boolean corsEnabled,
    String allowedOrigins
) {}
