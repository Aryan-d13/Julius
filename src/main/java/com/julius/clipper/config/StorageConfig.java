package com.julius.clipper.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.julius.clipper.config.properties.StorageProperties;
import com.julius.clipper.service.GcsStorageClient;
import com.julius.clipper.service.LocalStorageClient;
import com.julius.clipper.service.StorageClient;
import com.julius.clipper.telemetry.StorageMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Autowired
    private StorageMetrics storageMetrics;

    @Bean
    @ConditionalOnProperty(name = "clipper.storage.type", havingValue = "local", matchIfMissing = true)
    public StorageClient localStorageClient(StorageProperties storageProperties) {
        return new LocalStorageClient(storageProperties.local().root(), storageMetrics);
    }

    @Bean
    @ConditionalOnProperty(name = "clipper.storage.type", havingValue = "gcs")
    public StorageClient gcsStorageClient(StorageProperties storageProperties) {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        return new GcsStorageClient(storage, storageProperties.gcs().bucket(), storageMetrics);
    }
}
