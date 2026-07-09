package com.julius.clipper.config.validation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationValidationRunner implements InitializingBean {

    private final ConfigurationValidator configurationValidator;

    public ConfigurationValidationRunner(ConfigurationValidator configurationValidator) {
        this.configurationValidator = configurationValidator;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        configurationValidator.validateAll();
    }
}
