package com.julius.clipper.service;

import com.google.cloud.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URL;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class GcsStorageClientTest {

    private Storage mockStorage;
    private GcsStorageClient client;
    private final String bucketName = "test-bucket";

    @BeforeEach
    public void setUp() {
        mockStorage = mock(Storage.class);
        client = new GcsStorageClient(mockStorage, bucketName, null);
    }

    @Test
    public void testExists() {
        BlobId blobId = BlobId.of(bucketName, "test-key");
        Blob mockBlob = mock(Blob.class);
        when(mockStorage.get(blobId)).thenReturn(mockBlob);
        when(mockBlob.exists()).thenReturn(true);

        assertThat(client.exists("test-key")).isTrue();
        verify(mockStorage).get(blobId);
    }

    @Test
    public void testDelete() {
        BlobId blobId = BlobId.of(bucketName, "test-key");
        client.delete("test-key");
        verify(mockStorage).delete(blobId);
    }

    @Test
    public void testGenerateSignedUrl() throws Exception {
        URL mockUrl = new URL("https://signed-url.com/test-key");
        when(mockStorage.signUrl(
            any(BlobInfo.class),
            anyLong(),
            any(TimeUnit.class),
            any(Storage.SignUrlOption.class)
        )).thenReturn(mockUrl);

        String urlStr = client.generateSignedUrl("test-key", Duration.ofMinutes(15));
        assertThat(urlStr).isEqualTo("https://signed-url.com/test-key");
    }

    @Test
    public void testGetMetadata() {
        BlobId blobId = BlobId.of(bucketName, "test-key");
        Blob mockBlob = mock(Blob.class);
        when(mockStorage.get(blobId)).thenReturn(mockBlob);
        when(mockBlob.exists()).thenReturn(true);
        when(mockBlob.getName()).thenReturn("test-key");
        when(mockBlob.getSize()).thenReturn(1234L);
        when(mockBlob.getMd5ToHexString()).thenReturn("d41d8cd98f00b204e9800998ecf8427e");
        when(mockBlob.getContentType()).thenReturn("video/mp4");
        when(mockBlob.getCreateTimeOffsetDateTime()).thenReturn(OffsetDateTime.now());
        when(mockBlob.getMetadata()).thenReturn(Map.of("meta", "val"));

        StoredObject meta = client.getMetadata("test-key");
        assertThat(meta.key()).isEqualTo("test-key");
        assertThat(meta.sizeBytes()).isEqualTo(1234L);
        assertThat(meta.contentType()).isEqualTo("video/mp4");
        assertThat(meta.checksum()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
        assertThat(meta.metadata()).containsEntry("meta", "val");
    }
}
