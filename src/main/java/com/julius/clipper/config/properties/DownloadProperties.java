package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clipper.download")
public record DownloadProperties(
    @NotBlank String dir,
    String cookiesPath,
    @NotBlank String format
) {}
