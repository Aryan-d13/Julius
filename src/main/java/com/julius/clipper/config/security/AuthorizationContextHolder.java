package com.julius.clipper.config.security;

public class AuthorizationContextHolder {
    private static final ThreadLocal<AuthorizationContext> contextHolder = new ThreadLocal<>();

    public static void setContext(AuthorizationContext context) {
        contextHolder.set(context);
    }

    public static AuthorizationContext getContext() {
        return contextHolder.get();
    }

    public static void clearContext() {
        contextHolder.remove();
    }
}
