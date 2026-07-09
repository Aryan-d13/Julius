package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clipper.workspace")
public record WorkspaceProperties(
    @NotBlank String downloadDir,
    @NotBlank String convertDir,
    @NotBlank String cutDir,
    @NotBlank String cacheDir,
    @NotBlank String videoLibraryDir,
    @NotBlank String audioLibraryDir,
    @NotBlank String renderOutputDir
) {}
