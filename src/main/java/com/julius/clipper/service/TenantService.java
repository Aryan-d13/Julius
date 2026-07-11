package com.julius.clipper.service;

import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantService {

    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final InvitationRepository invitationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public TenantService(
            OrganizationRepository organizationRepository,
            WorkspaceRepository workspaceRepository,
            MembershipRepository membershipRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            InvitationRepository invitationRepository,
            RoleRepository roleRepository,
            UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.invitationRepository = invitationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Organization createOrganization(String name, String userId, boolean personal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Organization org = Organization.builder()
                .name(name)
                .personal(personal)
                .build();
        org = organizationRepository.save(org);

        Role ownerRole = roleRepository.findByName("ROLE_ORG_OWNER")
                .orElseThrow(() -> new IllegalStateException("ROLE_ORG_OWNER not seeded"));

        Membership membership = Membership.builder()
                .user(user)
                .organization(org)
                .role(ownerRole)
                .status("ACTIVE")
                .build();
        membershipRepository.save(membership);

        // Create default workspace
        createWorkspace(org.getId(), "Default Workspace", userId);

        return org;
    }

    @Transactional
    public Workspace createWorkspace(String orgId, String name, String userId) {
        Organization org = organizationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found or deleted"));

        Workspace ws = Workspace.builder()
                .organization(org)
                .name(name)
                .build();
        ws = workspaceRepository.save(ws);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Role wsAdminRole = roleRepository.findByName("ROLE_WORKSPACE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_WORKSPACE_ADMIN not seeded"));

        WorkspaceMembership wm = WorkspaceMembership.builder()
                .workspace(ws)
                .user(user)
                .role(wsAdminRole)
                .build();
        workspaceMembershipRepository.save(wm);

        return ws;
    }

    @Transactional
    public String inviteMember(String orgId, String email, String roleName, String inviterId) {
        Organization org = organizationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        Role targetRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid role name: " + roleName));

        // Generate dynamic secure token
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        Invitation invitation = Invitation.builder()
                .organization(org)
                .email(email)
                .role(targetRole)
                .tokenHash(tokenHash)
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        invitationRepository.save(invitation);
        
        // Return raw token for distribution (e.g. by email)
        return rawToken;
    }

    @Transactional
    public void acceptInvitation(String rawToken, String userId) {
        String tokenHash = hashToken(rawToken);
        Invitation invite = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token"));

        if (!"PENDING".equals(invite.getStatus())) {
            throw new IllegalStateException("Invitation is already processed: " + invite.getStatus());
        }

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus("EXPIRED");
            invitationRepository.save(invite);
            throw new IllegalStateException("Invitation has expired");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Create membership
        Membership membership = Membership.builder()
                .user(user)
                .organization(invite.getOrganization())
                .role(invite.getRole())
                .status("ACTIVE")
                .build();
        membershipRepository.save(membership);

        invite.setStatus("ACCEPTED");
        invitationRepository.save(invite);
    }

    @Transactional
    public void removeMember(String orgId, String userId) {
        membershipRepository.findActiveMembership(userId, orgId).ifPresent(m -> {
            m.setDeletedAt(LocalDateTime.now());
            membershipRepository.save(m);
        });
    }

    @Transactional
    public void changeMemberRole(String orgId, String userId, String newRoleName) {
        Membership m = membershipRepository.findActiveMembership(userId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

        Role role = roleRepository.findByName(newRoleName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid role: " + newRoleName));

        m.setRole(role);
        membershipRepository.save(m);
    }

    @Transactional
    public void transferOwnership(String orgId, String currentOwnerId, String targetUserId) {
        Membership currentOwner = membershipRepository.findActiveMembership(currentOwnerId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Current owner membership not found"));

        Membership targetUser = membershipRepository.findActiveMembership(targetUserId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Target user membership not found"));

        Role adminRole = roleRepository.findByName("ROLE_ORG_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ORG_ADMIN not seeded"));

        Role ownerRole = roleRepository.findByName("ROLE_ORG_OWNER")
                .orElseThrow(() -> new IllegalStateException("ROLE_ORG_OWNER not seeded"));

        currentOwner.setRole(adminRole);
        targetUser.setRole(ownerRole);

        membershipRepository.save(currentOwner);
        membershipRepository.save(targetUser);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token value", e);
        }
    }
}
