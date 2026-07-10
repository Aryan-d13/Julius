package com.julius.clipper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.Role;
import com.julius.clipper.domain.User;
import com.julius.clipper.domain.UserSession;
import com.julius.clipper.repository.RoleRepository;
import com.julius.clipper.repository.UserRepository;
import com.julius.clipper.repository.UserSessionRepository;
import com.julius.clipper.service.AuthService;
import com.julius.clipper.service.AuthService.AuthResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        // Clear repositories before each test
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        
        // Ensure ROLE_USER is seeded if it was deleted
        if (roleRepository.findByName("ROLE_USER").isEmpty()) {
            roleRepository.save(new Role("role-user-uuid-placeholder-1111", "ROLE_USER"));
        }
    }

    @Test
    public void testRegistrationAndLoginFlow() throws Exception {
        // 1. Register User
        Map<String, String> regRequest = Map.of(
                "email", "test@julius.com",
                "password", "SecurePassword123!",
                "fullName", "Julius Tester"
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));

        // Verify database state
        Optional<User> userOpt = userRepository.findByEmail("test@julius.com");
        assertThat(userOpt).isPresent();
        assertThat(userOpt.get().getFullName()).isEqualTo("Julius Tester");
        assertThat(userOpt.get().getRoles()).isNotEmpty();

        // 2. Login User
        Map<String, String> loginRequest = Map.of(
                "email", "test@julius.com",
                "password", "SecurePassword123!"
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    public void testAuthenticationSecurityBoundary() throws Exception {
        // Hitting protected endpoint without credentials -> HTTP 401
        mockMvc.perform(get("/api/jobs/non-existent-id"))
                .andExpect(status().isUnauthorized());

        // Create a user and authenticate directly
        User user = authService.register("auth@julius.com", "Password123!", "Auth Tester");
        AuthResponse auth = authService.login("auth@julius.com", "Password123!", "127.0.0.1", "agent", "corr", "req");

        // Request with valid Authorization Bearer token -> HTTP 404 (Not Found, but Authorized!)
        mockMvc.perform(get("/api/jobs/non-existent-id")
                .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isNotFound());

        // Request with valid HTTP-only access_token cookie -> HTTP 404 (Not Found, but Authorized!)
        Cookie cookie = new Cookie("access_token", auth.accessToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        mockMvc.perform(get("/api/jobs/non-existent-id")
                .cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testRefreshTokenRotationAndTheftDetection() throws Exception {
        User user = authService.register("rtr@julius.com", "Password123!", "RTR Tester");
        AuthResponse auth1 = authService.login("rtr@julius.com", "Password123!", "127.0.0.1", "agent", "corr", "req");

        // 1. Refresh with first token -> Returns new tokens
        Map<String, String> refreshReq = Map.of("refreshToken", auth1.refreshToken());
        String body = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, String> responseMap = objectMapper.readValue(body, Map.class);
        String nextAccessToken = responseMap.get("access_token");
        String nextRefreshToken = responseMap.get("refresh_token");

        assertThat(nextAccessToken).isNotEqualTo(auth1.accessToken());
        assertThat(nextRefreshToken).isNotEqualTo(auth1.refreshToken());

        // Verify the old session is updated to the new token, and rotation counter incremented
        Optional<UserSession> sessionOpt = sessionRepository.findById(auth1.sessionId());
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getRotationCounter()).isEqualTo(1);

        // 2. Reuse the first refresh token (simulates token theft / reuse) -> Expect invalidation of the entire session tree
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());

        // Verify that the session has now been revoked
        Optional<UserSession> sessionAfterTheft = sessionRepository.findById(auth1.sessionId());
        assertThat(sessionAfterTheft).isPresent();
        assertThat(sessionAfterTheft.get().isRevoked()).isTrue();
    }

    @Test
    public void testOAuth2FederatedLinking() {
        // Seed GITHUB provider attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 1234567);
        attributes.put("email", "oauth@julius.com");
        attributes.put("name", "OAuth GitHub User");

        // Call OAuth Callback logic directly
        AuthResponse auth = authService.loginOrLinkOAuth2("GITHUB", attributes, "127.0.0.1", "Mozilla", "corr", "req");

        assertThat(auth.accessToken()).isNotEmpty();
        assertThat(auth.refreshToken()).isNotEmpty();

        // Register password-based account under same email
        User user = userRepository.findByEmail("oauth@julius.com").orElseThrow();
        assertThat(user.getFullName()).isEqualTo("OAuth GitHub User");

        // Links Google account matching same email
        Map<String, Object> googleAttrs = new HashMap<>();
        googleAttrs.put("sub", "google-sub-999");
        googleAttrs.put("email", "oauth@julius.com");
        googleAttrs.put("name", "OAuth Google User");
        googleAttrs.put("email_verified", true);

        AuthResponse authGoogle = authService.loginOrLinkOAuth2("GOOGLE", googleAttrs, "127.0.0.1", "Mozilla", "corr", "req");
        assertThat(authGoogle.accessToken()).isNotEmpty();

        // Check user id is the same
        Optional<User> linkedUser = userRepository.findByEmail("oauth@julius.com");
        assertThat(linkedUser).isPresent();
        assertThat(linkedUser.get().getId()).isEqualTo(user.getId());
    }
}
