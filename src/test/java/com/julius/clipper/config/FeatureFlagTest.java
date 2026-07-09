package com.julius.clipper.config;

import com.julius.clipper.config.features.*;
import com.julius.clipper.config.properties.FeatureFlagsProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

public class FeatureFlagTest {

    @Test
    public void testLocalPropertyFeatureFlagProvider() {
        FeatureFlagsProperties props = new FeatureFlagsProperties(Map.of(
                "new-ui-enabled", true,
                "beta-algorithm", false
        ));

        LocalPropertyFeatureFlagProvider provider = new LocalPropertyFeatureFlagProvider(props);
        
        assertThat(provider.getEnabledState("new-ui-enabled")).contains(true);
        assertThat(provider.getEnabledState("beta-algorithm")).contains(false);
        assertThat(provider.getEnabledState("unknown-flag")).isEmpty();
    }

    @Test
    public void testFeatureFlagServiceChain() {
        // Mock provider 1: has flag configured
        FeatureFlagProvider provider1 = flagName -> {
            if ("flag-1".equals(flagName)) return Optional.of(true);
            return Optional.empty();
        };

        // Mock provider 2: has other flag configured
        FeatureFlagProvider provider2 = flagName -> {
            if ("flag-1".equals(flagName)) return Optional.of(false); // Should be ignored because provider 1 executes first
            if ("flag-2".equals(flagName)) return Optional.of(true);
            return Optional.empty();
        };

        FeatureFlagService service = new FeatureFlagService(List.of(provider1, provider2));

        assertThat(service.isEnabled("flag-1")).isTrue();
        assertThat(service.isEnabled("flag-2")).isTrue();
        assertThat(service.isEnabled("flag-3", false)).isFalse();
        assertThat(service.isEnabled("flag-3", true)).isTrue();
    }
}
