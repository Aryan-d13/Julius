package com.julius.clipper.service;

import java.time.Instant;
import java.util.Map;

public record StoredObject(
    String key,
    long sizeBytes,
    String checksum,
    String contentType,
    Instant createdAt,
    String publicUri,
    Map<String, String> metadata
) {}
