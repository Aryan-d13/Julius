package com.julius.clipper.config.features;

import java.util.Optional;

/**
 * Interface representing a source for resolving feature toggles.
 *
 * <p><strong>Architectural Contract for Implementers:</strong></p>
 * <ul>
 *   <li><b>Precedence:</b> Evaluated based on the class-level {@code @Order} annotation.
 *       Lower values take precedence (e.g., local properties override remote databases).</li>
 *   <li><b>Caching Expectations:</b> Operations must run in O(1) time. Remote providers
 *       must implement internal caching to prevent high-latency HTTP or database calls.</li>
 *   <li><b>Refresh Behavior:</b> Cache updates should occur asynchronously (e.g., via scheduler
 *       or pub/sub events) to avoid blocking thread requests.</li>
 *   <li><b>Failure Behavior:</b> If an external provider encounters a connection error or
 *       failure, it must catch the exception, log it at {@code ERROR} level, and return
 *       {@link Optional#empty()} to allow the fallback chain to continue.</li>
 *   <li><b>Default Behavior:</b> Return {@link Optional#empty()} if the flag key is not configured.</li>
 * </ul>
 */
public interface FeatureFlagProvider {
    Optional<Boolean> getEnabledState(String flagName);
}
