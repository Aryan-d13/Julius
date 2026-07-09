package com.julius.clipper.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "clipper")
public record FeatureFlagsProperties(
    Map<String, Boolean> features
) {
    public FeatureFlagsProperties {
        if (features == null) {
            features = Map.of();
        }
    }
}
