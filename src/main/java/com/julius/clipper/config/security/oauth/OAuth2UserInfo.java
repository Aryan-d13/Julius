package com.julius.clipper.config.security.oauth;

public record OAuth2UserInfo(
    String id,
    String email,
    String name,
    boolean emailVerified
) {}
