package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clipper.ai")
public record AiProperties(
    @NotBlank String geminiApiKey,
    @NotBlank String geminiModel,
    Whisper whisper
) {
    public record Whisper(
        @NotBlank String model,
        @NotBlank String pythonPath,
        String pythonEnv
    ) {}
}
