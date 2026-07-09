package com.julius.clipper.service;

import java.io.InputStream;
import java.util.Map;

public record UploadRequest(
    String key,
    InputStream content,
    long contentLength,
    String contentType,
    String md5Checksum,
    Map<String, String> metadata
) {}
