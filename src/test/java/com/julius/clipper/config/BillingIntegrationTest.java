package com.julius.clipper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import com.julius.clipper.service.AuthService;
import com.julius.clipper.service.AuthService.AuthResponse;
import com.julius.clipper.service.BillingService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BillingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private BillingPlanRepository billingPlanRepository;

    @Autowired
    private BillingJournalRepository journalRepository;

    @Autowired
    private BillingAccountRepository accountRepository;

    @Autowired
    private BillingTransactionRepository transactionRepository;

    @Autowired
    private BillingJournalEntryRepository journalEntryRepository;

    @Autowired
    private QuotaUsageSnapshotRepository quotaRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private User testUser;
    private Organization testOrg;
    private Workspace testWorkspace;
    private BillingPlan testPlan;
    private String userToken;
    private String adminToken;

    @BeforeEach
    public void setUp() {
        // Clear repositories to start with clean state
        quotaRepository.deleteAll();
        subscriptionRepository.deleteAll();
        billingPlanRepository.deleteAll();
        journalEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        journalRepository.deleteAll();
        membershipRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        // Seed basic role
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = Role.builder().name("ROLE_USER").build();
            return roleRepository.save(r);
        });

        // Register user -> automatically provisions organization & default workspace
        testUser = authService.register("billing-test@example.com", "secure-pass", "Billing Tester");
        List<Membership> memberships = membershipRepository.findActiveMembershipsForUser(testUser.getId());
        assertThat(memberships).isNotEmpty();
        
        testOrg = memberships.get(0).getOrganization();
        List<Workspace> workspaces = workspaceRepository.findByOrganizationIdAndDeletedAtIsNull(testOrg.getId());
        assertThat(workspaces).isNotEmpty();
        testWorkspace = workspaces.get(0);

        // Create billing plan
        testPlan = BillingPlan.builder()
                .name("Basic")
                .stripePriceId("price_basic_123")
                .amountMinorUnits(1900L)
                .currency("USD")
                .billingInterval("MONTHLY")
                .build();
        testPlan = billingPlanRepository.save(testPlan);

        // Authenticate test user
        AuthResponse userAuth = authService.login("billing-test@example.com", "secure-pass", "127.0.0.1", "agent", "corr", "req");
        this.userToken = userAuth.accessToken();

        // Setup Admin Operator
        Role adminRole = roleRepository.findByName("ROLE_OPERATOR_SUPER_ADMIN").orElseGet(() -> {
            Role r = Role.builder().name("ROLE_OPERATOR_SUPER_ADMIN").build();
            return roleRepository.save(r);
        });

        User adminUser = User.builder()
                .email("admin-test@example.com")
                .fullName("Admin User")
                .passwordHash(passwordEncoder.encode("secure-pass"))
                .roles(new HashSet<>(Set.of(adminRole)))
                .build();
        userRepository.save(adminUser);

        AuthResponse adminAuth = authService.login("admin-test@example.com", "secure-pass", "127.0.0.1", "agent", "corr", "req");
        this.adminToken = adminAuth.accessToken();
    }

    @Test
    public void testCheckoutSessionEndpoint() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.getOrDefault("successUrl", "http://localhost:3000/success");
        body.put("priceId", "price_basic_123");

        String response = mockMvc.perform(post("/api/billing/checkout")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> result = objectMapper.readValue(response, Map.class);
        assertThat(result.get("url")).isNotNull();
        assertThat(result.get("url").toString()).contains("session_id=");
    }

    @Test
    public void testStripeWebhookSubscriptionLifecycle() throws Exception {
        // 1. Send subscription created/updated webhook
        String webhookPayload = "{"
                + "\"id\": \"evt_test_123\","
                + "\"type\": \"customer.subscription.created\","
                + "\"data\": {"
                + "  \"object\": {"
                + "    \"id\": \"sub_stripe_123\","
                + "    \"customer\": \"cus_stripe_123\","
                + "    \"status\": \"active\","
                + "    \"current_period_start\": 1672531199,"
                + "    \"current_period_end\": 1704067199,"
                + "    \"metadata\": {\"organizationId\": \"" + testOrg.getId() + "\"},"
                + "    \"items\": {"
                + "      \"data\": [{"
                + "        \"price\": {\"id\": \"price_basic_123\"}"
                + "      }]"
                + "    }"
                + "  }"
                + "}"
                + "}";

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "mock_sig")
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // Verify subscription is saved and status mapped correctly
        Subscription sub = subscriptionRepository.findByOrganizationId(testOrg.getId())
                .orElse(null);
        assertThat(sub).isNotNull();
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getStripeSubscriptionId()).isEqualTo("sub_stripe_123");

        // Verify quota usage snapshot was created/updated for plan features
        QuotaUsageSnapshot renderQuota = quotaRepository.findByOrganizationIdAndFeatureId(testOrg.getId(), "RENDER_JOBS")
                .orElse(null);
        assertThat(renderQuota).isNotNull();
        assertThat(renderQuota.getLimitValue()).isEqualTo(50.0); // Basic tier limit

        // 2. Invoice payment succeeded webhook event
        String invoicePayload = "{"
                + "\"id\": \"evt_invoice_123\","
                + "\"type\": \"invoice.payment_succeeded\","
                + "\"data\": {"
                + "  \"object\": {"
                + "    \"id\": \"in_test_123\","
                + "    \"subscription\": \"sub_stripe_123\","
                + "    \"amount_paid\": 1900,"
                + "    \"currency\": \"usd\""
                + "  }"
                + "}"
                + "}";

        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "mock_sig")
                        .content(invoicePayload))
                .andExpect(status().isOk());

        // Verify double-entry ledger accounts and transactions were created
        long cashBalance = billingService.getAccountBalance(testOrg.getId(), "Cash (Stripe)");
        long revenueBalance = billingService.getAccountBalance(testOrg.getId(), "Subscription Revenue");
        assertThat(cashBalance).isEqualTo(1900L);
        assertThat(revenueBalance).isEqualTo(1900L); // balanced credit!
    }

    @Test
    public void testJobSubmitQuotaConsumptionAndFailureRecovery() throws Exception {
        // Setup initial default quota limit of 1.0 for testing
        quotaRepository.save(QuotaUsageSnapshot.builder()
                .organizationId(testOrg.getId())
                .featureId("RENDER_JOBS")
                .currentUsage(0.0)
                .limitValue(1.0) // set limit to 1 render job
                .isUnlimited(false)
                .build());

        // Submit Job 1 -> should succeed
        Map<String, Object> jobConfig = Map.of(
                "url", "https://youtube.com/watch?v=123",
                "count", 3,
                "copyLanguage", "en"
        );

        mockMvc.perform(post("/api/workspaces/" + testWorkspace.getId() + "/jobs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobConfig)))
                .andExpect(status().isAccepted());

        // Verify quota was consumed (usage should be 1.0)
        QuotaUsageSnapshot quota = quotaRepository.findByOrganizationIdAndFeatureId(testOrg.getId(), "RENDER_JOBS").get();
        assertThat(quota.getCurrentUsage()).isEqualTo(1.0);

        // Submit Job 2 -> should fail with PAYMENT_REQUIRED (limit exceeded)
        mockMvc.perform(post("/api/workspaces/" + testWorkspace.getId() + "/jobs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobConfig)))
                .andExpect(status().isPaymentRequired());

        // Revert usage manually using releaseQuota (simulating job failure)
        billingService.releaseQuota(testOrg.getId(), "RENDER_JOBS", 1.0);
        quota = quotaRepository.findByOrganizationIdAndFeatureId(testOrg.getId(), "RENDER_JOBS").get();
        assertThat(quota.getCurrentUsage()).isEqualTo(0.0);
    }

    @Test
    public void testAdminTopUpAndLedgerQuery() throws Exception {
        Map<String, Object> topUpBody = Map.of("amount", 2500);

        mockMvc.perform(post("/api/admin/billing/organizations/" + testOrg.getId() + "/topup")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(topUpBody)))
                .andExpect(status().isOk());

        // Verify prepaid deferred revenue balance increased
        long prepaidBalance = billingService.getAccountBalance(testOrg.getId(), "Prepaid Balance (Deferred Revenue)");
        assertThat(prepaidBalance).isEqualTo(2500L);

        // Query organization ledger endpoint
        String response = mockMvc.perform(get("/api/admin/billing/organizations/" + testOrg.getId() + "/ledger")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> result = objectMapper.readValue(response, Map.class);
        assertThat(result.get("prepaidBalanceMinorUnits")).isEqualTo(2500);
    }
}
