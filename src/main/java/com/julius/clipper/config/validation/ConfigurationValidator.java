package com.julius.clipper.config.validation;

import com.julius.clipper.config.properties.*;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationValidator {

    private final StorageProperties storageProperties;
    private final QueueProperties queueProperties;
    private final AiProperties aiProperties;
    private final TelemetryProperties telemetryProperties;
    private final SecurityProperties securityProperties;
    private final WorkerProperties workerProperties;
    private final DownloadProperties downloadProperties;
    private final WorkspaceProperties workspaceProperties;
    
    private final StoragePropertiesValidator storagePropertiesValidator;
    private final QueuePropertiesValidator queuePropertiesValidator;
    private final BeanValidator beanValidator;

    public ConfigurationValidator(
            StorageProperties storageProperties,
            QueueProperties queueProperties,
            AiProperties aiProperties,
            TelemetryProperties telemetryProperties,
            SecurityProperties securityProperties,
            WorkerProperties workerProperties,
            DownloadProperties downloadProperties,
            WorkspaceProperties workspaceProperties,
            StoragePropertiesValidator storagePropertiesValidator,
            QueuePropertiesValidator queuePropertiesValidator,
            BeanValidator beanValidator) {
        this.storageProperties = storageProperties;
        this.queueProperties = queueProperties;
        this.aiProperties = aiProperties;
        this.telemetryProperties = telemetryProperties;
        this.securityProperties = securityProperties;
        this.workerProperties = workerProperties;
        this.downloadProperties = downloadProperties;
        this.workspaceProperties = workspaceProperties;
        this.storagePropertiesValidator = storagePropertiesValidator;
        this.queuePropertiesValidator = queuePropertiesValidator;
        this.beanValidator = beanValidator;
    }

    public void validateAll() {
        // 1. Standard JSR-380 bean validations
        beanValidator.validate(storageProperties, "clipper.storage");
        beanValidator.validate(queueProperties, "clipper.queue");
        beanValidator.validate(aiProperties, "clipper.ai");
        beanValidator.validate(telemetryProperties, "clipper.telemetry");
        beanValidator.validate(securityProperties, "clipper.security");
        beanValidator.validate(workerProperties, "clipper.worker");
        beanValidator.validate(downloadProperties, "clipper.download");
        beanValidator.validate(workspaceProperties, "clipper.workspace");

        // 2. Custom conditional validations
        if (storageProperties != null) {
            storagePropertiesValidator.validate(storageProperties);
        }
        if (queueProperties != null) {
            queuePropertiesValidator.validate(queueProperties);
        }
    }
}
