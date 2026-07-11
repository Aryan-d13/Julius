package com.julius.clipper.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class AuthorizationContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            // Register new authorization context caching session parameters
            String userId = auth.getName(); // The token sub claim maps user UUID
            AuthorizationContextHolder.setContext(new AuthorizationContext(userId));
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Strictly clear ThreadLocal map on request thread recycling
            AuthorizationContextHolder.clearContext();
        }
    }
}
