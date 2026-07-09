package com.julius.clipper.config.validation;

public class ConfigurationValidationException extends RuntimeException {
    
    public ConfigurationValidationException(String message) {
        super(message);
    }

    public ConfigurationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
