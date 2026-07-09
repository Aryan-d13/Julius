package com.julius.clipper.config.validation;

import org.springframework.stereotype.Component;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import java.util.Set;

@Component
public class BeanValidator {

    private final Validator validator;

    public BeanValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(Object bean, String prefix) {
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
            throw new ConfigurationValidationException(sb.toString());
        }
    }
}
