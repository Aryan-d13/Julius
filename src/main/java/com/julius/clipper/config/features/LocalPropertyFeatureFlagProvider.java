package com.julius.clipper.config.features;

import com.julius.clipper.config.properties.FeatureFlagsProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@Order(100)
public class LocalPropertyFeatureFlagProvider implements FeatureFlagProvider {

    private final FeatureFlagsProperties properties;

    public LocalPropertyFeatureFlagProvider(FeatureFlagsProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Boolean> getEnabledState(String flagName) {
        if (properties.features() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(properties.features().get(flagName));
    }
}
