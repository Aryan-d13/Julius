package com.julius.clipper.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@ConfigurationProperties(prefix = "clipper.queue")
public record QueueProperties(
    @NotBlank String type,
    Redis redis
) implements Validator {

    public record Redis(String host, Integer port, String password) {}

    @Override
    public boolean supports(Class<?> clazz) {
        return QueueProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        QueueProperties props = (QueueProperties) target;
        if ("redis".equalsIgnoreCase(props.type())) {
            if (props.redis() == null) {
                errors.rejectValue("redis", "NotNull", "Redis configuration must not be null when type is redis");
                return;
            }
            if (props.redis().host() == null || props.redis().host().isBlank()) {
                errors.rejectValue("redis.host", "NotBlank", "Redis host must not be blank when type is redis");
            }
            if (props.redis().port() == null) {
                errors.rejectValue("redis.port", "NotNull", "Redis port must not be null when type is redis");
            }
        }
    }
}
