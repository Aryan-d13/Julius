package com.julius.clipper.service;

import com.google.cloud.storage.*;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GcsStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(GcsStorageClient.class);
    private final Storage storage;
    private final String bucketName;
    private final MeterRegistry meterRegistry;

    public GcsStorageClient(Storage storage, String bucketName, MeterRegistry meterRegistry) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.meterRegistry = meterRegistry;
        log.info("Initialized GCS Storage Client for bucket: {}", bucketName);
    }

    @Override
    public StoredObject upload(UploadRequest request) {
        long startTime = System.nanoTime();
        log.debug("GCS storage upload starting for key: {}", request.key());
        
        BlobId blobId = BlobId.of(bucketName, request.key());
        BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(blobId);
        
        if (request.contentType() != null) {
            blobInfoBuilder.setContentType(request.contentType());
        }
        if (request.md5Checksum() != null) {
            blobInfoBuilder.setMd5(request.md5Checksum());
        }
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            blobInfoBuilder.setMetadata(request.metadata());
        }
        
        BlobInfo blobInfo = blobInfoBuilder.build();

        try {
            long bytesWritten = 0;
            // Write stream to GCS using WriteChannel
            try (WriteChannel writer = storage.writer(blobInfo);
                 InputStream in = request.content()) {
                
                byte[] buffer = new byte[8192];
                int readBytes;
                while ((readBytes = in.read(buffer)) != -1) {
                    writer.write(java.nio.ByteBuffer.wrap(buffer, 0, readBytes));
                    bytesWritten += readBytes;
                }
            }

            // Retrieve created blob metadata to return exact values
            Blob blob = storage.get(blobId);
            if (blob == null) {
                throw new RuntimeException("Blob not found after upload: " + request.key());
            }

            StoredObject storedObject = new StoredObject(
                blob.getName(),
                blob.getSize(),
                blob.getMd5ToHexString() != null ? blob.getMd5ToHexString() : blob.getEtag(),
                blob.getContentType(),
                Instant.ofEpochMilli(blob.getCreateTimeOffsetDateTime().toInstant().toEpochMilli()),
                "https://storage.googleapis.com/" + bucketName + "/" + blob.getName(),
                blob.getMetadata() != null ? blob.getMetadata() : Map.of()
            );

            recordOperationMetric("upload", bytesWritten, System.nanoTime() - startTime, null);
            log.info("GCS upload succeeded for key: {}, size={} bytes", request.key(), bytesWritten);
            return storedObject;

        } catch (Exception e) {
            recordOperationMetric("upload", 0, System.nanoTime() - startTime, e.getClass().getSimpleName());
            throw new RuntimeException("GCS upload failed for key: " + request.key(), e);
        }
    }

    @Override
    public void download(String key, OutputStream destination) {
        long startTime = System.nanoTime();
        log.debug("GCS storage download starting for key: {}", key);
        BlobId blobId = BlobId.of(bucketName, key);

        try {
            Blob blob = storage.get(blobId);
            if (blob == null) {
                recordOperationMetric("download", 0, System.nanoTime() - startTime, "BlobNotFoundException");
                throw new RuntimeException("GCS storage object not found: " + key);
            }

            long size = blob.getSize();
            try (ReadChannel reader = storage.reader(blobId);
                 WritableByteChannel destChannel = Channels.newChannel(destination)) {
                
                long bytesTransferred = 0;
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8192);
                while (reader.read(buffer) > 0) {
                    buffer.flip();
                    destChannel.write(buffer);
                    bytesTransferred += buffer.limit();
                    buffer.clear();
                }
            }

            recordOperationMetric("download", size, System.nanoTime() - startTime, null);
            log.debug("GCS download completed for key: {}, size={} bytes", key, size);

        } catch (Exception e) {
            recordOperationMetric("download", 0, System.nanoTime() - startTime, e.getClass().getSimpleName());
            throw new RuntimeException("GCS download failed for key: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        BlobId blobId = BlobId.of(bucketName, key);
        Blob blob = storage.get(blobId);
        return blob != null && blob.exists();
    }

    @Override
    public void delete(String key) {
        log.info("GCS deleting key: {}", key);
        BlobId blobId = BlobId.of(bucketName, key);
        storage.delete(blobId);
    }

    @Override
    public StoredObject getMetadata(String key) {
        BlobId blobId = BlobId.of(bucketName, key);
        Blob blob = storage.get(blobId);
        if (blob == null || !blob.exists()) {
            throw new RuntimeException("GCS storage object not found: " + key);
        }

        return new StoredObject(
            blob.getName(),
            blob.getSize(),
            blob.getMd5ToHexString() != null ? blob.getMd5ToHexString() : blob.getEtag(),
            blob.getContentType(),
            Instant.ofEpochMilli(blob.getCreateTimeOffsetDateTime().toInstant().toEpochMilli()),
            "https://storage.googleapis.com/" + bucketName + "/" + blob.getName(),
            blob.getMetadata() != null ? blob.getMetadata() : Map.of()
        );
    }

    @Override
    public String generateSignedUrl(String key, Duration expiration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, key).build();
        URL url = storage.signUrl(
            blobInfo,
            expiration.toMillis(),
            TimeUnit.MILLISECONDS,
            SignUrlOption.withV4Signature()
        );
        return url.toString();
    }

    private void recordOperationMetric(String op, long bytes, long durationNs, String exception) {
        if (meterRegistry == null) return;
        try {
            String prov = "gcs";
            meterRegistry.counter("clipper.storage.bytes." + (op.equals("upload") ? "uploaded" : "downloaded")).increment(bytes);
            
            io.micrometer.core.instrument.Tags tags = io.micrometer.core.instrument.Tags.of(
                "provider", prov,
                "operation", op
            );
            
            if (exception != null) {
                meterRegistry.counter("clipper.storage.failures", tags.and("exception", exception)).increment();
            }
            
            meterRegistry.timer("clipper.storage.operation.duration", tags).record(durationNs, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.warn("Failed to register metric: {}", e.getMessage());
        }
    }
}
