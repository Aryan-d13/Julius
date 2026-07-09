package com.julius.clipper.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.julius.clipper.telemetry.StorageMetrics;

public class LocalStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageClient.class);
    private final String rootDir;
    private final StorageMetrics storageMetrics;

    public LocalStorageClient(String rootDir, StorageMetrics storageMetrics) {
        this.rootDir = rootDir;
        this.storageMetrics = storageMetrics;
        try {
            Files.createDirectories(Paths.get(rootDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize local storage root directory: " + rootDir, e);
        }
    }

    @Override
    public StoredObject upload(UploadRequest request) {
        long startTime = System.nanoTime();
        log.debug("Local storage upload starting for key: {}", request.key());
        Path targetPath = Paths.get(rootDir, request.key());

        try {
            Files.createDirectories(targetPath.getParent());
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            long bytesWritten = 0;

            try (InputStream in = request.content();
                 OutputStream out = Files.newOutputStream(targetPath);
                 DigestInputStream digestIn = new DigestInputStream(in, md5Digest)) {

                byte[] buffer = new byte[8192];
                int readBytes;
                while ((readBytes = digestIn.read(buffer)) != -1) {
                    out.write(buffer, 0, readBytes);
                    bytesWritten += readBytes;
                }
            }

            String checksum = HexFormat.of().formatHex(md5Digest.digest());

            if (request.md5Checksum() != null && !request.md5Checksum().equalsIgnoreCase(checksum)) {
                Files.deleteIfExists(targetPath);
                throw new IOException("Integrity check failed: uploaded checksum " + checksum + 
                                    " did not match expected checksum " + request.md5Checksum());
            }

            StoredObject storedObject = new StoredObject(
                request.key(),
                bytesWritten,
                checksum,
                request.contentType() != null ? request.contentType() : "application/octet-stream",
                Instant.now(),
                "file://" + targetPath.toAbsolutePath(),
                request.metadata() != null ? request.metadata() : Map.of()
            );

            // Write metadata sidecar file
            writeSidecarMetadata(request.key(), storedObject);

            // Publish metrics
            recordOperationMetric("upload", bytesWritten, System.nanoTime() - startTime, null);

            log.info("Local storage upload succeeded for key: {}, size={} bytes", request.key(), bytesWritten);
            return storedObject;

        } catch (Exception e) {
            recordOperationMetric("upload", 0, System.nanoTime() - startTime, e.getClass().getSimpleName());
            throw new RuntimeException("Local storage upload failed for key: " + request.key(), e);
        }
    }

    @Override
    public void download(String key, OutputStream destination) {
        long startTime = System.nanoTime();
        log.debug("Local storage download starting for key: {}", key);
        Path sourcePath = Paths.get(rootDir, key);

        if (!Files.exists(sourcePath)) {
            recordOperationMetric("download", 0, System.nanoTime() - startTime, "FileNotFoundException");
            throw new RuntimeException("Storage object not found: " + key);
        }

        try {
            long bytesRead = 0;
            try (InputStream in = Files.newInputStream(sourcePath)) {
                byte[] buffer = new byte[8192];
                int readBytes;
                while ((readBytes = in.read(buffer)) != -1) {
                    destination.write(buffer, 0, readBytes);
                    bytesRead += readBytes;
                }
            }

            recordOperationMetric("download", bytesRead, System.nanoTime() - startTime, null);
            log.debug("Local storage download completed for key: {}, size={} bytes", key, bytesRead);

        } catch (Exception e) {
            recordOperationMetric("download", 0, System.nanoTime() - startTime, e.getClass().getSimpleName());
            throw new RuntimeException("Local storage download failed for key: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        Path path = Paths.get(rootDir, key);
        return Files.exists(path) && Files.isRegularFile(path);
    }

    @Override
    public void delete(String key) {
        log.info("Local storage deleting key: {}", key);
        try {
            Path path = Paths.get(rootDir, key);
            Path sidecarPath = Paths.get(rootDir, key + ".metadata.json");
            Files.deleteIfExists(path);
            Files.deleteIfExists(sidecarPath);
        } catch (IOException e) {
            throw new RuntimeException("Local storage deletion failed for key: " + key, e);
        }
    }

    @Override
    public StoredObject getMetadata(String key) {
        Path path = Paths.get(rootDir, key);
        if (!Files.exists(path)) {
            throw new RuntimeException("Storage object metadata not found: " + key);
        }

        StoredObject cached = readSidecarMetadata(key);
        if (cached != null) {
            return cached;
        }

        // Fallback build from raw file metadata if sidecar missing
        try {
            long size = Files.size(path);
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            return new StoredObject(
                key,
                size,
                "",
                "application/octet-stream",
                modified,
                "file://" + path.toAbsolutePath(),
                Map.of()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read metadata for local path: " + path, e);
        }
    }

    @Override
    public String generateSignedUrl(String key, Duration expiration) {
        Path path = Paths.get(rootDir, key);
        // Emulate GCS signed URL string format
        long expiresTimestamp = Instant.now().plus(expiration).getEpochSecond();
        return "http://localhost:8080/api/dev/storage/download?key=" + key + "&expires=" + expiresTimestamp + "&signature=mock_local_sig";
    }

    private void writeSidecarMetadata(String key, StoredObject object) {
        Path sidecarPath = Paths.get(rootDir, key + ".metadata.json");
        try {
            Files.createDirectories(sidecarPath.getParent());
            // Create a crude JSON line for metadata serialization to avoid heavy mapper imports here
            String json = String.format(
                "{\"key\":\"%s\",\"sizeBytes\":%d,\"checksum\":\"%s\",\"contentType\":\"%s\",\"createdAt\":\"%s\",\"publicUri\":\"%s\"}",
                object.key(), object.sizeBytes(), object.checksum(), object.contentType(), object.createdAt(), object.publicUri()
            );
            Files.writeString(sidecarPath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("Failed to write metadata sidecar for key: {}", key, e);
        }
    }

    private StoredObject readSidecarMetadata(String key) {
        Path sidecarPath = Paths.get(rootDir, key + ".metadata.json");
        if (!Files.exists(sidecarPath)) {
            return null;
        }
        try {
            String json = Files.readString(sidecarPath);
            // Simple manual extraction to prevent jackson parse overhead
            String keyVal = extractJsonValue(json, "key");
            long size = Long.parseLong(extractJsonValue(json, "sizeBytes"));
            String checksum = extractJsonValue(json, "checksum");
            String type = extractJsonValue(json, "contentType");
            Instant created = Instant.parse(extractJsonValue(json, "createdAt"));
            String uri = extractJsonValue(json, "publicUri");

            return new StoredObject(keyVal, size, checksum, type, created, uri, Map.of());
        } catch (Exception e) {
            log.warn("Failed to read metadata sidecar for key: {}", key, e);
            return null;
        }
    }

    private String extractJsonValue(String json, String field) {
        String pattern = "\"" + field + "\":\"?([^,\"}]+)\"?";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private void recordOperationMetric(String op, long bytes, long durationNs, String exception) {
        if (storageMetrics == null) return;
        try {
            String prov = "local";
            if (bytes > 0) {
                storageMetrics.recordBytes(op, prov, bytes);
            }
            if (exception != null) {
                storageMetrics.recordFailure(op, prov, exception);
            }
            storageMetrics.recordDuration(op, prov, durationNs, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.warn("Failed to register metric: {}", e.getMessage());
        }
    }
}
