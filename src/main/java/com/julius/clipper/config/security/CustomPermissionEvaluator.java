package com.julius.clipper.config.security;

import com.julius.clipper.domain.Membership;
import com.julius.clipper.domain.Workspace;
import com.julius.clipper.domain.WorkspaceMembership;
import com.julius.clipper.repository.MembershipRepository;
import com.julius.clipper.repository.RoleRepository;
import com.julius.clipper.repository.WorkspaceMembershipRepository;
import com.julius.clipper.repository.WorkspaceRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.stereotype.Component;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final MembershipRepository membershipRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceRepository workspaceRepository;
    private final RoleRepository roleRepository;

    public CustomPermissionEvaluator(
            MembershipRepository membershipRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            WorkspaceRepository workspaceRepository,
            RoleRepository roleRepository) {
        this.membershipRepository = membershipRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceRepository = workspaceRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null || permission == null) {
            return false;
        }

        // Support System Admin override
        boolean isSystemAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_SYSTEM_ADMIN".equals(a));
        if (isSystemAdmin) {
            return true;
        }

        String userId = authentication.getName();
        String resourceId = targetId.toString();
        String permissionName = permission.toString();

        AuthorizationContext ctx = AuthorizationContextHolder.getContext();
        if (ctx != null && ctx.hasCachedPermissions(resourceId)) {
            return ctx.getCachedPermissions(resourceId).contains(permissionName);
        }

        Set<String> permissions = new HashSet<>();
        if ("ORGANIZATION".equalsIgnoreCase(targetType)) {
            permissions = resolveOrgPermissions(userId, resourceId);
        } else if ("WORKSPACE".equalsIgnoreCase(targetType)) {
            permissions = resolveWorkspacePermissions(userId, resourceId);
        }

        if (ctx != null) {
            ctx.cachePermissions(resourceId, permissions);
        }

        return permissions.contains(permissionName);
    }

    private Set<String> resolveOrgPermissions(String userId, String orgId) {
        Optional<Membership> membershipOpt = membershipRepository.findActiveMembership(userId, orgId);
        if (membershipOpt.isEmpty()) {
            return Set.of();
        }
        List<String> perms = roleRepository.findPermissionsByRoleId(membershipOpt.get().getRole().getId());
        return new HashSet<>(perms);
    }

    private Set<String> resolveWorkspacePermissions(String userId, String workspaceId) {
        Optional<Workspace> wsOpt = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId);
        if (wsOpt.isEmpty()) {
            return Set.of();
        }

        Workspace ws = wsOpt.get();
        String orgId = ws.getOrganization().getId();

        // 1. Owners & Admins inherit access to all nested workspaces
        Optional<Membership> orgMembershipOpt = membershipRepository.findActiveMembership(userId, orgId);
        if (orgMembershipOpt.isPresent()) {
            String roleName = orgMembershipOpt.get().getRole().getName();
            if ("ROLE_ORG_OWNER".equals(roleName)) {
                return Set.of("workspace.manage", "jobs.create", "jobs.delete", "jobs.share");
            } else if ("ROLE_ORG_ADMIN".equals(roleName)) {
                return Set.of("jobs.create", "jobs.share", "workspace.manage");
            } 
        }

        // 2. Check direct Workspace membership
        Optional<WorkspaceMembership> wsMembershipOpt = workspaceMembershipRepository.findActiveMembership(userId, workspaceId);
        if (wsMembershipOpt.isEmpty()) {
            return Set.of();
        }

        List<String> perms = roleRepository.findPermissionsByRoleId(wsMembershipOpt.get().getRole().getId());
        return new HashSet<>(perms);
    }
}
