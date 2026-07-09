package com.julius.clipper.config.validation;

import com.julius.clipper.config.properties.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import java.util.Set;

@Component
public class ConfigurationValidator implements InitializingBean {

    private final StorageProperties storageProperties;
    private final QueueProperties queueProperties;
    private final AiProperties aiProperties;
    private final TelemetryProperties telemetryProperties;
    private final SecurityProperties securityProperties;
    private final WorkerProperties workerProperties;
    
    private final StoragePropertiesValidator storagePropertiesValidator;
    private final QueuePropertiesValidator queuePropertiesValidator;
    
    private final Validator validator;

    public ConfigurationValidator(
            StorageProperties storageProperties,
            QueueProperties queueProperties,
            AiProperties aiProperties,
            TelemetryProperties telemetryProperties,
            SecurityProperties securityProperties,
            WorkerProperties workerProperties,
            StoragePropertiesValidator storagePropertiesValidator,
            QueuePropertiesValidator queuePropertiesValidator,
            Validator validator) {
        this.storageProperties = storageProperties;
        this.queueProperties = queueProperties;
        this.aiProperties = aiProperties;
        this.telemetryProperties = telemetryProperties;
        this.securityProperties = securityProperties;
        this.workerProperties = workerProperties;
        this.storagePropertiesValidator = storagePropertiesValidator;
        this.queuePropertiesValidator = queuePropertiesValidator;
        this.validator = validator;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 1. Run standard JSR-380 validation
        validatePropertyBean(storageProperties, "clipper.storage");
        validatePropertyBean(queueProperties, "clipper.queue");
        validatePropertyBean(aiProperties, "clipper.ai");
        validatePropertyBean(telemetryProperties, "clipper.telemetry");
        validatePropertyBean(securityProperties, "clipper.security");
        validatePropertyBean(workerProperties, "clipper.worker");

        // 2. Run conditional validations using injected validators
        if (storageProperties != null) {
            storagePropertiesValidator.validate(storageProperties);
        }
        if (queueProperties != null) {
            queuePropertiesValidator.validate(queueProperties);
        }
    }

    private void validatePropertyBean(Object bean, String prefix) {
        if (bean == null) {
            return;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(bean);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Configuration validation failed for prefix '").append(prefix).append("':\n");
            for (ConstraintViolation<Object> violation : violations) {
                sb.append(" - ").append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("\n");
            }
            throw new IllegalStateException(sb.toString());
        }
    }
}
