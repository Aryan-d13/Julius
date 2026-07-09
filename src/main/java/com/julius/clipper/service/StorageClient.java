package com.julius.clipper.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

public interface StorageClient {
    /**
     * Uploads an object from an input stream with metadata.
     *
     * @param request the upload request details
     * @return the metadata snapshot of the stored object
     */
    StoredObject upload(UploadRequest request);

    /**
     * Downloads an object's contents into a destination output stream.
     *
     * @param key         the storage key identifier
     * @param destination the destination output stream
     */
    void download(String key, OutputStream destination);

    /**
     * Checks if an object exists in storage.
     *
     * @param key the storage key identifier
     * @return true if the object exists, false otherwise
     */
    boolean exists(String key);

    /**
     * Deletes an object from storage.
     *
     * @param key the storage key identifier
     */
    void delete(String key);

    /**
     * Retrieves the metadata snapshot for an object.
     *
     * @param key the storage key identifier
     * @return the metadata snapshot
     */
    StoredObject getMetadata(String key);

    /**
     * Generates a temporary signed URL for public download access.
     *
     * @param key        the storage key identifier
     * @param expiration the time duration until link expiration
     * @return the temporary signed URL
     */
    String generateSignedUrl(String key, Duration expiration);
}
