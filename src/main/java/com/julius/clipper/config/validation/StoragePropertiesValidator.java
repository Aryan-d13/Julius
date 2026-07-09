package com.julius.clipper.config.validation;

import com.julius.clipper.config.properties.StorageProperties;
import org.springframework.stereotype.Component;

@Component
public class StoragePropertiesValidator {

    public void validate(StorageProperties props) {
        if ("local".equalsIgnoreCase(props.type())) {
            if (props.local() == null || props.local().root() == null || props.local().root().isBlank()) {
                throw new ConfigurationValidationException("Configuration validation failed for prefix 'clipper.storage':\n" +
                        " - local.root: must not be blank when clipper.storage.type is 'local'");
            }
        } else if ("gcs".equalsIgnoreCase(props.type())) {
            if (props.gcs() == null || props.gcs().bucket() == null || props.gcs().bucket().isBlank()) {
                throw new ConfigurationValidationException("Configuration validation failed for prefix 'clipper.storage':\n" +
                        " - gcs.bucket: must not be blank when clipper.storage.type is 'gcs'");
            }
        }
    }
}
