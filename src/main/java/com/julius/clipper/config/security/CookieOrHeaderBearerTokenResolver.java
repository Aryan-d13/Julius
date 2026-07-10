package com.julius.clipper.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
public class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        // 1. Try Authorization header first
        String header = request.getHeader("Authorization");
        if (header != null && header.toLowerCase().startsWith("bearer ")) {
            return header.substring(7).trim();
        }
        
        // 2. Try HTTP-only access_token cookie
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
