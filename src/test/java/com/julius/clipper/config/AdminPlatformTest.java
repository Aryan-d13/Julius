package com.julius.clipper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import com.julius.clipper.service.AuthService;
import com.julius.clipper.service.AuthService.AuthResponse;
import com.julius.clipper.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AdminPlatformTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private InternalNoteRepository internalNoteRepository;

    @Autowired
    private AdminAuditEventRepository adminAuditEventRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        adminAuditEventRepository.deleteAll();
        internalNoteRepository.deleteAll();
        jobRepository.deleteAll();
        workspaceRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();

        // Seed default system roles
        if (roleRepository.findByName("ROLE_USER").isEmpty()) {
            roleRepository.save(new Role("role-user-uuid-placeholder-1111", "ROLE_USER"));
        }
        if (roleRepository.findByName("ROLE_OPERATOR_SUPER_ADMIN").isEmpty()) {
            roleRepository.save(new Role("role-op-super-admin-9999", "ROLE_OPERATOR_SUPER_ADMIN"));
        }
        if (roleRepository.findByName("ROLE_ORG_OWNER").isEmpty()) {
            roleRepository.save(new Role("role-org-owner-uuid-1111", "ROLE_ORG_OWNER"));
        }
        if (roleRepository.findByName("ROLE_WORKSPACE_ADMIN").isEmpty()) {
            roleRepository.save(new Role("role-ws-admin-uuid-4444", "ROLE_WORKSPACE_ADMIN"));
        }
    }

    @Test
    public void testAdminSecurityBoundary() throws Exception {
        // Standard user login
        User normalUser = authService.register("normal@julius.com", "Password123!", "Normal User");
        AuthResponse normalAuth = authService.login("normal@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");

        // Standard user tries to hit search -> Expect 403 Forbidden
        mockMvc.perform(get("/api/admin/search?q=normal")
                .header("Authorization", "Bearer " + normalAuth.accessToken()))
                .andExpect(status().isForbidden());

        // Admin registers and logs in
        Role superAdminRole = roleRepository.findByName("ROLE_OPERATOR_SUPER_ADMIN").orElseThrow();
        User adminUser = User.builder()
                .email("admin2@julius.com")
                .fullName("Staff Admin")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .roles(new HashSet<>(Set.of(superAdminRole)))
                .build();
        userRepository.save(adminUser);

        AuthResponse adminAuth2 = authService.login("admin2@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");

        // Admin succeeds hitting search -> Expect 200 OK
        mockMvc.perform(get("/api/admin/search?q=normal")
                .header("Authorization", "Bearer " + adminAuth2.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    public void testGlobalSearchAndSupportWorkflows() throws Exception {
        Role superAdminRole = roleRepository.findByName("ROLE_OPERATOR_SUPER_ADMIN").orElseThrow();
        User admin = User.builder()
                .email("staff@julius.com")
                .fullName("Staff User")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .roles(new HashSet<>(Set.of(superAdminRole)))
                .build();
        userRepository.save(admin);
        
        AuthResponse adminAuth = authService.login("staff@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");

        // Register User A to populate searchable data
        authService.register("usera@julius.com", "Password123!", "User Alpha");

        // Query search
        mockMvc.perform(get("/api/admin/search?q=alpha")
                .header("Authorization", "Bearer " + adminAuth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isNotEmpty())
                .andExpect(jsonPath("$.users[0].fullName").value("User Alpha"));
    }

    @Test
    public void testInternalNotesLifecycle() throws Exception {
        Role superAdminRole = roleRepository.findByName("ROLE_OPERATOR_SUPER_ADMIN").orElseThrow();
        User admin = User.builder()
                .email("staff2@julius.com")
                .fullName("Staff User 2")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .roles(new HashSet<>(Set.of(superAdminRole)))
                .build();
        userRepository.save(admin);
        
        AuthResponse adminAuth = authService.login("staff2@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");

        User customer = authService.register("customer@julius.com", "Password123!", "Customer User");

        Map<String, String> notePayload = Map.of(
                "entityType", "USER",
                "entityId", customer.getId(),
                "noteText", "This customer has high compute priority."
        );

        // Operator posts internal note
        mockMvc.perform(post("/api/admin/notes")
                .header("Authorization", "Bearer " + adminAuth.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(notePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteText").value("This customer has high compute priority."));

        // Retrieve notes
        mockMvc.perform(get("/api/admin/notes/USER/" + customer.getId())
                .header("Authorization", "Bearer " + adminAuth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].noteText").value("This customer has high compute priority."));

        // Verify audit logs registered
        assertThat(adminAuditEventRepository.findAll()).isNotEmpty();
        assertThat(adminAuditEventRepository.findAll().get(0).getAction()).isEqualTo("NOTE_ATTACHED");
    }

    @Test
    public void testTimelineTraversal() throws Exception {
        Role superAdminRole = roleRepository.findByName("ROLE_OPERATOR_SUPER_ADMIN").orElseThrow();
        User admin = User.builder()
                .email("staff3@julius.com")
                .fullName("Staff User 3")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .roles(new HashSet<>(Set.of(superAdminRole)))
                .build();
        userRepository.save(admin);
        
        AuthResponse adminAuth = authService.login("staff3@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");

        User user = authService.register("timeline@julius.com", "Password123!", "Timeline User");

        // Fetch User Timeline
        String response = mockMvc.perform(get("/api/admin/users/" + user.getId() + "/timeline")
                .header("Authorization", "Bearer " + adminAuth.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> timeline = objectMapper.readValue(response, List.class);
        boolean hasRegisterEvent = timeline.stream().anyMatch(t -> "USER_REGISTERED".equals(t.get("event")));
        assertThat(hasRegisterEvent).isTrue();
    }
}
