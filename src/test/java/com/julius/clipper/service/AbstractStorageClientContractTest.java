package com.julius.clipper.service;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractStorageClientContractTest {

    protected abstract StorageClient getClient();

    @Test
    public void testFullUploadDownloadDeleteCycle() throws Exception {
        StorageClient client = getClient();
        String key = "test/subfolder/file_" + System.currentTimeMillis() + ".txt";
        String content = "Hello Julius Cloud Storage Contract Test! \uD83D\uDE80";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        // 1. Verify existence is false initially
        assertThat(client.exists(key)).isFalse();

        // 2. Upload object
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            UploadRequest request = new UploadRequest(
                key,
                in,
                bytes.length,
                "text/plain",
                null,
                Map.of("test-owner", "staff-engineer")
            );
            StoredObject stored = client.upload(request);
            assertThat(stored.key()).isEqualTo(key);
            assertThat(stored.sizeBytes()).isEqualTo(bytes.length);
            assertThat(stored.contentType()).isEqualTo("text/plain");
            assertThat(stored.createdAt()).isBeforeOrEqualTo(Instant.now());
        }

        // 3. Verify existence is true
        assertThat(client.exists(key)).isTrue();

        // 4. Retrieve metadata
        StoredObject meta = client.getMetadata(key);
        assertThat(meta.key()).isEqualTo(key);
        assertThat(meta.sizeBytes()).isEqualTo(bytes.length);

        // 5. Download content
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        client.download(key, out);
        String downloadedContent = out.toString(StandardCharsets.UTF_8);
        assertThat(downloadedContent).isEqualTo(content);

        // 6. Generate signed URL
        String signedUrl = client.generateSignedUrl(key, Duration.ofMinutes(15));
        assertThat(signedUrl).isNotNull();
        assertThat(signedUrl).contains(key);

        // 7. Delete object
        client.delete(key);
        assertThat(client.exists(key)).isFalse();

        // 8. Verify metadata throws exception after delete
        assertThatThrownBy(() -> client.getMetadata(key))
                .isInstanceOf(RuntimeException.class);
    }
}
