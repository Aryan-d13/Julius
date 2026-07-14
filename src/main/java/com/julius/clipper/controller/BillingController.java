package com.julius.clipper.controller;

import com.julius.clipper.domain.Membership;
import com.julius.clipper.domain.Subscription;
import com.julius.clipper.repository.MembershipRepository;
import com.julius.clipper.repository.SubscriptionRepository;
import com.julius.clipper.service.BillingProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingProvider billingProvider;
    private final SubscriptionRepository subscriptionRepository;
    private final MembershipRepository membershipRepository;
    private final org.springframework.security.access.PermissionEvaluator permissionEvaluator;

    public BillingController(
            BillingProvider billingProvider,
            SubscriptionRepository subscriptionRepository,
            MembershipRepository membershipRepository,
            org.springframework.security.access.PermissionEvaluator permissionEvaluator) {
        this.billingProvider = billingProvider;
        this.subscriptionRepository = subscriptionRepository;
        this.membershipRepository = membershipRepository;
        this.permissionEvaluator = permissionEvaluator;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, String> body) {
        try {
            String priceId = body.get("priceId");
            if (priceId == null || priceId.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required parameter: priceId");
            }

            String userId = SecurityContextHolder.getContext().getAuthentication().getName();
            List<Membership> memberships = membershipRepository.findActiveMembershipsForUser(userId);
            if (memberships.isEmpty()) {
                throw new IllegalStateException("User does not belong to any active organization");
            }
            String orgId = memberships.get(0).getOrganization().getId();

            String successUrl = body.getOrDefault("successUrl", "http://localhost:3000/billing/success?session_id={CHECKOUT_SESSION_ID}");
            String cancelUrl = body.getOrDefault("cancelUrl", "http://localhost:3000/billing/cancel");

            checkOrganizationPermission(orgId, "billing.manage");

            String checkoutUrl = billingProvider.createCheckoutSession(orgId, priceId, successUrl, cancelUrl);
            return ResponseEntity.ok(Map.of("url", checkoutUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/portal")
    public ResponseEntity<?> getPortalSession(
            @RequestParam(value = "returnUrl", defaultValue = "http://localhost:3000/billing") String returnUrl) {
        try {
            String userId = SecurityContextHolder.getContext().getAuthentication().getName();
            List<Membership> memberships = membershipRepository.findActiveMembershipsForUser(userId);
            if (memberships.isEmpty()) {
                throw new IllegalStateException("User does not belong to any active organization");
            }
            String orgId = memberships.get(0).getOrganization().getId();

            checkOrganizationPermission(orgId, "billing.manage");

            String portalUrl = billingProvider.createCustomerPortalSession(orgId, returnUrl);
            return ResponseEntity.ok(Map.of("url", portalUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/subscription")
    public ResponseEntity<?> getSubscription() {
        try {
            String userId = SecurityContextHolder.getContext().getAuthentication().getName();
            List<Membership> memberships = membershipRepository.findActiveMembershipsForUser(userId);
            if (memberships.isEmpty()) {
                throw new IllegalStateException("User does not belong to any active organization");
            }
            String orgId = memberships.get(0).getOrganization().getId();

            Subscription sub = subscriptionRepository.findByOrganizationId(orgId).orElse(null);
            if (sub == null) {
                return ResponseEntity.ok(Map.of("status", "INACTIVE"));
            }

            return ResponseEntity.ok(Map.of(
                    "id", sub.getId(),
                    "organizationId", sub.getOrganizationId(),
                    "planName", sub.getPlan().getName(),
                    "status", sub.getStatus(),
                    "currentPeriodStart", sub.getCurrentPeriodStart(),
                    "currentPeriodEnd", sub.getCurrentPeriodEnd(),
                    "canceledAt", sub.getCanceledAt() != null ? sub.getCanceledAt() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false, defaultValue = "mock_sig") String signatureHeader) {
        try {
            billingProvider.handleWebhookEvent(payload, signatureHeader);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private void checkOrganizationPermission(String orgId, String permission) {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasPermission = permissionEvaluator.hasPermission(auth, orgId, "ORGANIZATION", permission);
        if (!hasPermission) {
            throw new org.springframework.security.access.AccessDeniedException("User does not have permission " + permission + " in organization " + orgId);
        }
    }
}
