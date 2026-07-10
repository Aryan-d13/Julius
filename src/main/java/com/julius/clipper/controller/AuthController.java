package com.julius.clipper.controller;

import com.julius.clipper.service.AuthService;
import com.julius.clipper.service.AuthService.AuthResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            authService.register(request.email(), request.password(), request.fullName());
            Map<String, String> body = new HashMap<>();
            body.put("message", "User registered successfully");
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            String ip = getClientIp(servletRequest);
            String userAgent = servletRequest.getHeader("User-Agent");
            String correlationId = servletRequest.getHeader("X-Correlation-ID");
            String requestId = servletRequest.getHeader("X-Request-ID");

            AuthResponse response = authService.login(
                    request.email(),
                    request.password(),
                    ip,
                    userAgent,
                    correlationId,
                    requestId
            );

            setAuthCookies(servletResponse, response.accessToken(), response.refreshToken());

            Map<String, String> body = new HashMap<>();
            body.put("access_token", response.accessToken());
            body.put("refresh_token", response.refreshToken());
            body.put("session_id", response.sessionId());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(401).body(error);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            String ip = getClientIp(servletRequest);
            String userAgent = servletRequest.getHeader("User-Agent");
            String correlationId = servletRequest.getHeader("X-Correlation-ID");
            String requestId = servletRequest.getHeader("X-Request-ID");

            // Extract refresh token from body or cookie
            String refreshToken = null;
            if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
                refreshToken = request.refreshToken();
            } else if (servletRequest.getCookies() != null) {
                for (Cookie cookie : servletRequest.getCookies()) {
                    if ("refresh_token".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                    }
                }
            }

            if (refreshToken == null || refreshToken.isBlank()) {
                throw new IllegalArgumentException("Refresh token is missing");
            }

            AuthResponse response = authService.refresh(
                    refreshToken,
                    ip,
                    userAgent,
                    correlationId,
                    requestId
            );

            setAuthCookies(servletResponse, response.accessToken(), response.refreshToken());

            Map<String, String> body = new HashMap<>();
            body.put("access_token", response.accessToken());
            body.put("refresh_token", response.refreshToken());
            body.put("session_id", response.sessionId());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(401).body(error);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        
        String refreshToken = null;
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshToken = request.refreshToken();
        } else if (servletRequest.getCookies() != null) {
            for (Cookie cookie : servletRequest.getCookies()) {
                if ("refresh_token".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                }
            }
        }

        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        clearAuthCookies(servletResponse);

        Map<String, String> body = new HashMap<>();
        body.put("message", "Logged out successfully");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/oauth2/callback")
    public ResponseEntity<?> oauth2Callback(
            @RequestParam String provider,
            @RequestBody Map<String, Object> attributes,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            String ip = getClientIp(servletRequest);
            String userAgent = servletRequest.getHeader("User-Agent");
            String correlationId = servletRequest.getHeader("X-Correlation-ID");
            String requestId = servletRequest.getHeader("X-Request-ID");

            AuthResponse response = authService.loginOrLinkOAuth2(
                    provider,
                    attributes,
                    ip,
                    userAgent,
                    correlationId,
                    requestId
            );

            setAuthCookies(servletResponse, response.accessToken(), response.refreshToken());

            Map<String, String> body = new HashMap<>();
            body.put("access_token", response.accessToken());
            body.put("refresh_token", response.refreshToken());
            body.put("session_id", response.sessionId());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(900)
                .sameSite("Lax")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(604800)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static record RegisterRequest(String email, String password, String fullName) {}
    public static record LoginRequest(String email, String password) {}
    public static record RefreshRequest(String refreshToken) {}
}
