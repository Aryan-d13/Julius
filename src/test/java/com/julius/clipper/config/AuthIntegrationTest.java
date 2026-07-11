package com.julius.clipper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.Role;
import com.julius.clipper.domain.User;
import com.julius.clipper.domain.UserSession;
import com.julius.clipper.domain.Workspace;
import com.julius.clipper.repository.RoleRepository;
import com.julius.clipper.repository.UserRepository;
import com.julius.clipper.repository.UserSessionRepository;
import com.julius.clipper.repository.WorkspaceRepository;
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
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        sessionRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
        
        if (roleRepository.findByName("ROLE_USER").isEmpty()) {
            roleRepository.save(new Role("role-user-uuid-placeholder-1111", "ROLE_USER"));
        }
        if (roleRepository.findByName("ROLE_ORG_OWNER").isEmpty()) {
            roleRepository.save(new Role("role-org-owner-uuid-1111", "ROLE_ORG_OWNER"));
        }
        if (roleRepository.findByName("ROLE_WORKSPACE_ADMIN").isEmpty()) {
            roleRepository.save(new Role("role-ws-admin-uuid-4444", "ROLE_WORKSPACE_ADMIN"));
        }
    }

    @Test
    public void testRegistrationAndLoginFlow() throws Exception {
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

        Optional<User> userOpt = userRepository.findByEmail("test@julius.com");
        assertThat(userOpt).isPresent();

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
        mockMvc.perform(get("/api/workspaces/ws-id-placeholder/jobs/non-existent-id"))
                .andExpect(status().isUnauthorized());

        User user = authService.register("auth@julius.com", "Password123!", "Auth Tester");
        AuthResponse auth = authService.login("auth@julius.com", "Password123!", "127.0.0.1", "agent", "corr", "req");

        // Resolve user's auto-provisioned workspace ID
        Workspace workspace = workspaceRepository.findAll().stream()
                .filter(w -> w.getOrganization().getName().contains("Auth Tester"))
                .findFirst().orElseThrow();
        String wsId = workspace.getId();

        mockMvc.perform(get("/api/workspaces/" + wsId + "/jobs/non-existent-id")
                .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isNotFound());

        Cookie cookie = new Cookie("access_token", auth.accessToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        mockMvc.perform(get("/api/workspaces/" + wsId + "/jobs/non-existent-id")
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

        // Verify session rotation counter incremented
        Optional<UserSession> sessionOpt = sessionRepository.findById(auth1.sessionId());
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getRotationCounter()).isEqualTo(1);

        // 2. Reuse old token within grace window (e.g. immediate callback retry) -> Blocked but session is NOT revoked
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Concurrent refresh request. Please retry."));

        Optional<UserSession> sessionDuringGrace = sessionRepository.findById(auth1.sessionId());
        assertThat(sessionDuringGrace).isPresent();
        assertThat(sessionDuringGrace.get().isRevoked()).isFalse(); // Grace window preserves session

        // 3. Reuse old token AFTER grace window (simulates actual token theft replay) -> Revokes entire session tree
        UserSession s = sessionRepository.findById(auth1.sessionId()).orElseThrow();
        s.setLastUsedAt(LocalDateTime.now().minusSeconds(30)); // Mock grace window expiry
        sessionRepository.saveAndFlush(s);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Session expired or token reused. Please sign in again."));

        Optional<UserSession> sessionAfterGrace = sessionRepository.findById(auth1.sessionId());
        assertThat(sessionAfterGrace).isPresent();
        assertThat(sessionAfterGrace.get().isRevoked()).isTrue(); // Theft detected and session revoked
    }

    @Test
    public void testOAuth2FederatedLinking() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 1234567);
        attributes.put("email", "oauth@julius.com");
        attributes.put("name", "OAuth GitHub User");

        AuthResponse auth = authService.loginOrLinkOAuth2("GITHUB", attributes, "127.0.0.1", "Mozilla", "corr", "req");

        assertThat(auth.accessToken()).isNotEmpty();
        assertThat(auth.refreshToken()).isNotEmpty();

        User user = userRepository.findByEmail("oauth@julius.com").orElseThrow();
        assertThat(user.getFullName()).isEqualTo("OAuth GitHub User");

        Map<String, Object> googleAttrs = new HashMap<>();
        googleAttrs.put("sub", "google-sub-999");
        googleAttrs.put("email", "oauth@julius.com");
        googleAttrs.put("name", "OAuth Google User");
        googleAttrs.put("email_verified", true);

        AuthResponse authGoogle = authService.loginOrLinkOAuth2("GOOGLE", googleAttrs, "127.0.0.1", "Mozilla", "corr", "req");
        assertThat(authGoogle.accessToken()).isNotEmpty();

        Optional<User> linkedUser = userRepository.findByEmail("oauth@julius.com");
        assertThat(linkedUser).isPresent();
        assertThat(linkedUser.get().getId()).isEqualTo(user.getId());
    }
}
