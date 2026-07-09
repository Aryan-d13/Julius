package com.julius.clipper.config.validation;

import com.julius.clipper.config.properties.QueueProperties;
import org.springframework.stereotype.Component;

@Component
public class QueuePropertiesValidator {

    public void validate(QueueProperties props) {
        if ("redis".equalsIgnoreCase(props.type())) {
            if (props.redis() == null) {
                throw new IllegalStateException("Configuration validation failed for prefix 'clipper.queue':\n" +
                        " - redis: must not be null when clipper.queue.type is 'redis'");
            }
            if (props.redis().host() == null || props.redis().host().isBlank()) {
                throw new IllegalStateException("Configuration validation failed for prefix 'clipper.queue':\n" +
                        " - redis.host: must not be blank when clipper.queue.type is 'redis'");
            }
            if (props.redis().port() == null) {
                throw new IllegalStateException("Configuration validation failed for prefix 'clipper.queue':\n" +
                        " - redis.port: must not be null when clipper.queue.type is 'redis'");
            }
        }
    }
}
