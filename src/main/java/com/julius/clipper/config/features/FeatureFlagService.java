package com.julius.clipper.config.features;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service orchestrating feature flag lookups across registered providers.
 *
 * <p><strong>Precedence Rule:</strong></p>
 * <p>Providers are evaluated in the order of their Spring bean injection precedence,
 * controlled by the {@code @Order} annotation. The first provider returning a present
 * value wins. If no provider has the flag configured, the default value is returned.</p>
 *
 * <p><strong>Future Contextual Evaluations:</strong></p>
 * <p>To support contextual toggling (tenant-level, canary releases, rollout ratios),
 * the API can be extended with a context object, for example:</p>
 * <pre>{@code
 * public boolean isEnabled(String flagName, FeatureContext context, boolean defaultValue)
 * }</pre>
 */
@Service
public class FeatureFlagService {

    private final List<FeatureFlagProvider> providers;

    public FeatureFlagService(List<FeatureFlagProvider> providers) {
        this.providers = providers;
    }

    public boolean isEnabled(String flagName) {
        return isEnabled(flagName, false);
    }

    public boolean isEnabled(String flagName, boolean defaultValue) {
        for (FeatureFlagProvider provider : providers) {
            Optional<Boolean> state = provider.getEnabledState(flagName);
            if (state.isPresent()) {
                return state.get();
            }
        }
        return defaultValue;
    }
}
