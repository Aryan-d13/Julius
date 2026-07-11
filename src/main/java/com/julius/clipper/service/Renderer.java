package com.julius.clipper.service;

import com.julius.clipper.domain.RenderProfile;
import java.io.File;
import java.util.concurrent.CompletableFuture;

public interface Renderer {
    CompletableFuture<String> render(
            String inputPath,
            double startTime,
            double endTime,
            RenderProfile profile,
            File subtitleFile,
            String outputFilename
    );
}
