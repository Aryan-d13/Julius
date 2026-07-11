package com.julius.clipper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FontRegistry {
    private static final Logger log = LoggerFactory.getLogger(FontRegistry.class);

    private final String fontsDir = "data/library/fonts";
    private final HttpClient httpClient;

    private final Set<String> whitelistedFonts = Set.of(
            "Inter", "Montserrat", "Roboto", "Poppins", "Impact", "Arial"
    );

    // Maps Font Name -> URL to download TTF binary file from Google Fonts/Github
    private final Map<String, String> fontUrls = Map.of(
            "Inter", "https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Bold.ttf",
            "Montserrat", "https://github.com/JulietaUla/Montserrat/raw/master/fonts/ttf/Montserrat-Bold.ttf",
            "Roboto", "https://github.com/google/fonts/raw/main/apache/roboto/static/Roboto-Bold.ttf",
            "Poppins", "https://github.com/google/fonts/raw/main/ofl/poppins/Poppins-Bold.ttf"
    );

    private final Map<String, String> cachedPaths = new ConcurrentHashMap<>();

    public FontRegistry() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        File dir = new File(fontsDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public synchronized String getFontPath(String fontName) {
        String name = fontName.trim();
        if (!whitelistedFonts.contains(name)) {
            log.warn("Requested font '{}' is not whitelisted. Falling back to system font.", name);
            return null; // FFmpeg will fall back to default Arial/system fonts
        }

        // Standard system font lookup bypass
        if ("Impact".equalsIgnoreCase(name) || "Arial".equalsIgnoreCase(name)) {
            return null; // System font registered in FFmpeg env
        }

        if (cachedPaths.containsKey(name)) {
            return cachedPaths.get(name);
        }

        File fontFile = new File(fontsDir, name + "-Bold.ttf");
        if (fontFile.exists() && fontFile.length() > 1024) {
            String absolutePath = fontFile.getAbsolutePath();
            cachedPaths.put(name, absolutePath);
            return absolutePath;
        }

        String downloadUrl = fontUrls.get(name);
        if (downloadUrl == null) {
            return null;
        }

        log.info("Downloading and caching whitelisted font '{}' from source: {}", name, downloadUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(fontFile.toPath()));
            if (response.statusCode() == 200 && fontFile.exists() && fontFile.length() > 1024) {
                String path = fontFile.getAbsolutePath();
                cachedPaths.put(name, path);
                log.info("Successfully whitelisted and cached font path: {}", path);
                return path;
            } else {
                log.error("Font download failed with status code: {}", response.statusCode());
                if (fontFile.exists()) fontFile.delete();
            }
        } catch (Exception e) {
            log.error("Failed to retrieve font '{}': {}", name, e.getMessage(), e);
            if (fontFile.exists()) fontFile.delete();
        }

        return null;
    }
}
