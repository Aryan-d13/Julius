package com.julius.clipper.config.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AuthorizationContext {
    private final String userId;
    private final Map<String, Set<String>> resourcePermissions = new HashMap<>();

    public AuthorizationContext(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public boolean hasCachedPermissions(String resourceId) {
        return resourcePermissions.containsKey(resourceId);
    }

    public Set<String> getCachedPermissions(String resourceId) {
        return resourcePermissions.get(resourceId);
    }

    public void cachePermissions(String resourceId, Set<String> permissions) {
        resourcePermissions.put(resourceId, permissions);
    }
}
