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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TenantSecurityTest {

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
    private MembershipRepository membershipRepository;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        workspaceMembershipRepository.deleteAll();
        membershipRepository.deleteAll();
        workspaceRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();

        // Ensure roles are seeded
        if (roleRepository.findByName("ROLE_USER").isEmpty()) {
            roleRepository.save(new Role("role-user-uuid-placeholder-1111", "ROLE_USER"));
        }
        if (roleRepository.findByName("ROLE_ORG_OWNER").isEmpty()) {
            roleRepository.save(new Role("role-org-owner-uuid-1111", "ROLE_ORG_OWNER"));
        }
        if (roleRepository.findByName("ROLE_ORG_ADMIN").isEmpty()) {
            roleRepository.save(new Role("role-org-admin-uuid-2222", "ROLE_ORG_ADMIN"));
        }
        if (roleRepository.findByName("ROLE_ORG_MEMBER").isEmpty()) {
            roleRepository.save(new Role("role-org-member-uuid-3333", "ROLE_ORG_MEMBER"));
        }
        if (roleRepository.findByName("ROLE_WORKSPACE_ADMIN").isEmpty()) {
            roleRepository.save(new Role("role-ws-admin-uuid-4444", "ROLE_WORKSPACE_ADMIN"));
        }
    }

    @Test
    public void testPersonalWorkspaceProvisioningOnRegister() {
        User user = authService.register("tenant@julius.com", "Password123!", "Tenant Tester");
        
        // Assert Organization created automatically
        Optional<Workspace> personalWorkspace = workspaceRepository.findAll().stream()
                .filter(w -> w.getOrganization().getName().contains("Tenant Tester"))
                .findFirst();
        
        assertThat(personalWorkspace).isPresent();
        assertThat(personalWorkspace.get().getName()).isEqualTo("Default Workspace");
        
        Optional<Membership> membership = membershipRepository.findActiveMembership(user.getId(), personalWorkspace.get().getOrganization().getId());
        assertThat(membership).isPresent();
        assertThat(membership.get().getRole().getName()).isEqualTo("ROLE_ORG_OWNER");
    }

    @Test
    public void testWorkspaceIDORProtection() throws Exception {
        // User A registers
        User userA = authService.register("userA@julius.com", "Password123!", "User A");
        AuthResponse authA = authService.login("userA@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");
        Workspace workspaceA = workspaceRepository.findAll().stream()
                .filter(w -> w.getOrganization().getName().contains("User A"))
                .findFirst().orElseThrow();

        // User B registers
        User userB = authService.register("userB@julius.com", "Password123!", "User B");
        AuthResponse authB = authService.login("userB@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");

        // User B attempts to access User A's workspace jobs -> Expect 403 Forbidden
        mockMvc.perform(get("/api/workspaces/" + workspaceA.getId() + "/jobs/non-existent-id")
                .header("Authorization", "Bearer " + authB.accessToken()))
                .andExpect(status().isForbidden());

        // User A succeeds accessing their own workspace jobs -> Expect 404 Not Found (since job ID is non-existent)
        mockMvc.perform(get("/api/workspaces/" + workspaceA.getId() + "/jobs/non-existent-id")
                .header("Authorization", "Bearer " + authA.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testInvitationAcceptanceLifecycle() throws Exception {
        User owner = authService.register("owner@julius.com", "Password123!", "Owner User");
        AuthResponse authOwner = authService.login("owner@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");
        
        Organization org = organizationRepository.findAll().stream()
                .filter(o -> o.getName().contains("Owner User"))
                .findFirst().orElseThrow();

        // Recipient signs up
        User recipient = authService.register("recipient@julius.com", "Password123!", "Recipient User");
        AuthResponse authRecipient = authService.login("recipient@julius.com", "Password123!", "127.0.0.1", "agent", "c", "r");

        // Owner invites Recipient as Admin
        String invitePayload = objectMapper.writeValueAsString(Map.of(
                "email", "recipient@julius.com",
                "role", "ROLE_ORG_ADMIN"
        ));

        String responseBody = mockMvc.perform(post("/api/organizations/" + org.getId() + "/invitations")
                .header("Authorization", "Bearer " + authOwner.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invitePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitation_token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Map<String, String> resp = objectMapper.readValue(responseBody, Map.class);
        String token = resp.get("invitation_token");

        // Recipient accepts invitation
        mockMvc.perform(post("/api/organizations/invitations/" + token + "/accept")
                .header("Authorization", "Bearer " + authRecipient.accessToken()))
                .andExpect(status().isOk());

        // Verify membership created
        Optional<Membership> activeMembership = membershipRepository.findActiveMembership(recipient.getId(), org.getId());
        assertThat(activeMembership).isPresent();
        assertThat(activeMembership.get().getRole().getName()).isEqualTo("ROLE_ORG_ADMIN");
    }

    @Test
    public void testSoftDeletionMapping() {
        User user = authService.register("softdelete@julius.com", "Password123!", "SoftDelete Owner");
        Organization org = organizationRepository.findAll().stream()
                .filter(o -> o.getName().contains("SoftDelete Owner"))
                .findFirst().orElseThrow();

        assertThat(org.getDeletedAt()).isNull();

        org.setDeletedAt(LocalDateTime.now());
        organizationRepository.saveAndFlush(org);

        Optional<Organization> lookup = organizationRepository.findByIdAndDeletedAtIsNull(org.getId());
        assertThat(lookup).isEmpty();
    }
}
