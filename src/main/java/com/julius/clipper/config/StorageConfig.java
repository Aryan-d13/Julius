package com.julius.clipper.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.julius.clipper.service.GcsStorageClient;
import com.julius.clipper.service.LocalStorageClient;
import com.julius.clipper.service.StorageClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Bean
    @ConditionalOnProperty(name = "clipper.storage.type", havingValue = "local", matchIfMissing = true)
    public StorageClient localStorageClient(
            @Value("${clipper.storage.local.root:data/storage}") String rootDir) {
        return new LocalStorageClient(rootDir, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "clipper.storage.type", havingValue = "gcs")
    public StorageClient gcsStorageClient(
            @Value("${clipper.storage.gcs.bucket:julius-media-storage}") String bucketName) {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        return new GcsStorageClient(storage, bucketName, meterRegistry);
    }
}
