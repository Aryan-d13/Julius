package com.julius.clipper.controller;

import com.julius.clipper.domain.Organization;
import com.julius.clipper.service.TenantService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final TenantService tenantService;

    public OrganizationController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<?> createOrganization(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            String userId = SecurityContextHolder.getContext().getAuthentication().getName();
            Organization org = tenantService.createOrganization(name, userId, false);
            return ResponseEntity.ok(org);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{orgId}/invitations")
    @PreAuthorize("hasPermission(#orgId, 'ORGANIZATION', 'org.invite')")
    public ResponseEntity<?> inviteMember(
            @PathVariable String orgId,
            @RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            String roleName = body.get("role");
            String inviterId = SecurityContextHolder.getContext().getAuthentication().getName();
            String rawToken = tenantService.inviteMember(orgId, email, roleName, inviterId);
            
            Map<String, String> response = new HashMap<>();
            response.put("invitation_token", rawToken);
            response.put("message", "Invitation issued successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<?> acceptInvitation(@PathVariable String token) {
        try {
            String userId = SecurityContextHolder.getContext().getAuthentication().getName();
            tenantService.acceptInvitation(token, userId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Invitation accepted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    @PreAuthorize("hasPermission(#orgId, 'ORGANIZATION', 'org.settings.edit')")
    public ResponseEntity<?> removeMember(@PathVariable String orgId, @PathVariable String userId) {
        try {
            tenantService.removeMember(orgId, userId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Member removed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{orgId}/members/{userId}/role")
    @PreAuthorize("hasPermission(#orgId, 'ORGANIZATION', 'org.settings.edit')")
    public ResponseEntity<?> changeMemberRole(
            @PathVariable String orgId,
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        try {
            String newRole = body.get("role");
            tenantService.changeMemberRole(orgId, userId, newRole);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Role updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{orgId}/transfer-ownership")
    @PreAuthorize("hasPermission(#orgId, 'ORGANIZATION', 'org.delete')")
    public ResponseEntity<?> transferOwnership(
            @PathVariable String orgId,
            @RequestBody Map<String, String> body) {
        try {
            String currentOwnerId = SecurityContextHolder.getContext().getAuthentication().getName();
            String targetUserId = body.get("targetUserId");
            tenantService.transferOwnership(orgId, currentOwnerId, targetUserId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Ownership transferred successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
