package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@ConfigurationProperties(prefix = "clipper.storage")
public record StorageProperties(
    @NotBlank String type,
    Local local,
    Gcs gcs
) implements Validator {

    public record Local(String root) {}
    public record Gcs(String bucket) {}

    @Override
    public boolean supports(Class<?> clazz) {
        return StorageProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        StorageProperties props = (StorageProperties) target;
        if ("local".equalsIgnoreCase(props.type())) {
            if (props.local() == null || props.local().root() == null || props.local().root().isBlank()) {
                errors.rejectValue("local.root", "NotBlank", "Local root path must not be blank when type is local");
            }
        } else if ("gcs".equalsIgnoreCase(props.type())) {
            if (props.gcs() == null || props.gcs().bucket() == null || props.gcs().bucket().isBlank()) {
                errors.rejectValue("gcs.bucket", "NotBlank", "GCS bucket name must not be blank when type is gcs");
            }
        }
    }
}
