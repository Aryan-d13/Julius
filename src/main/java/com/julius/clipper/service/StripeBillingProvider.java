package com.julius.clipper.service;

import com.julius.clipper.domain.*;
import com.julius.clipper.repository.*;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import com.stripe.net.Webhook;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StripeBillingProvider implements BillingProvider {
    private static final Logger log = LoggerFactory.getLogger(StripeBillingProvider.class);

    private final SubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final BillingJournalRepository billingJournalRepository;
    private final WebhookIdempotencyRepository webhookIdempotencyRepository;
    private final SubscriptionStateHistoryRepository stateHistoryRepository;
    private final com.julius.clipper.service.BillingService billingService;

    @Value("${clipper.billing.stripe.api-key:mock}")
    private String apiKey;

    @Value("${clipper.billing.stripe.webhook-secret:mock_secret}")
    private String webhookSecret;

    public StripeBillingProvider(
            SubscriptionRepository subscriptionRepository,
            BillingPlanRepository billingPlanRepository,
            BillingJournalRepository billingJournalRepository,
            WebhookIdempotencyRepository webhookIdempotencyRepository,
            SubscriptionStateHistoryRepository stateHistoryRepository,
            com.julius.clipper.service.BillingService billingService) {
        this.subscriptionRepository = subscriptionRepository;
        this.billingPlanRepository = billingPlanRepository;
        this.billingJournalRepository = billingJournalRepository;
        this.webhookIdempotencyRepository = webhookIdempotencyRepository;
        this.stateHistoryRepository = stateHistoryRepository;
        this.billingService = billingService;
    }

    @PostConstruct
    public void init() {
        if (!isMockMode()) {
            Stripe.apiKey = apiKey;
            log.info("Initialized Stripe Billing Provider with live SDK client API key.");
        } else {
            log.info("Initialized Stripe Billing Provider in mock/simulation mode.");
        }
    }

    private boolean isMockMode() {
        return apiKey == null || apiKey.isEmpty() || "mock".equalsIgnoreCase(apiKey) || apiKey.startsWith("sk_test_mock");
    }

    @Override
    @Transactional
    public String createCheckoutSession(String organizationId, String priceId, String successUrl, String cancelUrl) {
        log.info("Creating checkout session for organization={}, priceId={}", organizationId, priceId);
        
        // Ensure organization has a journal and stripe customer initialized
        BillingJournal journal = billingJournalRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    BillingJournal j = BillingJournal.builder()
                            .organizationId(organizationId)
                            .stripeCustomerId(isMockMode() ? "cus_" + UUID.randomUUID().toString().substring(0, 8) : null)
                            .currency("USD")
                            .build();
                    return billingJournalRepository.save(j);
                });

        if (!isMockMode()) {
            try {
                // If stripe customer not present, create one in Stripe
                if (journal.getStripeCustomerId() == null) {
                    Map<String, Object> customerParams = new HashMap<>();
                    customerParams.put("metadata", Map.of("organizationId", organizationId));
                    com.stripe.model.Customer customer = com.stripe.model.Customer.create(customerParams);
                    journal.setStripeCustomerId(customer.getId());
                    billingJournalRepository.save(journal);
                }

                SessionCreateParams params = SessionCreateParams.builder()
                        .setCustomer(journal.getStripeCustomerId())
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setClientReferenceId(organizationId)
                        .addLineItem(SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build())
                        .putMetadata("organizationId", organizationId)
                        .build();

                Session session = Session.create(params);
                return session.getUrl();
            } catch (Exception e) {
                log.error("Failed to create Stripe checkout session", e);
                throw new RuntimeException("Stripe Checkout error: " + e.getMessage(), e);
            }
        } else {
            // Mock Mode URL
            String mockSessionId = "cs_mock_" + UUID.randomUUID();
            return successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id=" + mockSessionId + "&org_id=" + organizationId + "&price_id=" + priceId;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String createCustomerPortalSession(String organizationId, String returnUrl) {
        log.info("Creating customer portal session for organization={}", organizationId);
        BillingJournal journal = billingJournalRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("No billing journal found for organization: " + organizationId));

        String customerId = journal.getStripeCustomerId();
        if (customerId == null) {
            throw new IllegalArgumentException("No Stripe Customer associated with organization: " + organizationId);
        }

        if (!isMockMode()) {
            try {
                com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(returnUrl)
                        .build();
                com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);
                return session.getUrl();
            } catch (Exception e) {
                log.error("Failed to create Stripe portal session", e);
                throw new RuntimeException("Stripe Portal error: " + e.getMessage(), e);
            }
        } else {
            return returnUrl + (returnUrl.contains("?") ? "&" : "?") + "portal=success";
        }
    }

    @Override
    @Transactional
    public void syncSubscription(String stripeSubscriptionId) {
        log.info("Synchronizing Stripe subscription: {}", stripeSubscriptionId);
        if (isMockMode()) {
            log.info("Skipping subscription sync because provider is in mock mode.");
            return;
        }

        try {
            com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(stripeSubscriptionId);
            String orgId = stripeSub.getMetadata().get("organizationId");
            if (orgId == null) {
                orgId = stripeSub.getCustomer(); // fallback or custom mapping
            }

            String priceId = stripeSub.getItems().getData().get(0).getPrice().getId();
            BillingPlan plan = billingPlanRepository.findByStripePriceId(priceId)
                    .orElseThrow(() -> new IllegalStateException("Billing plan not found for price: " + priceId));

            String status = stripeSub.getStatus().toUpperCase();
            LocalDateTime periodStart = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), ZoneOffset.UTC);
            LocalDateTime periodEnd = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneOffset.UTC);
            LocalDateTime canceledAt = stripeSub.getCanceledAt() != null ?
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCanceledAt()), ZoneOffset.UTC) : null;

            updateSubscriptionInternal(orgId, plan, stripeSubscriptionId, status, periodStart, periodEnd, canceledAt, "STRIPE_SYNC");
        } catch (Exception e) {
            log.error("Failed to sync subscription: " + stripeSubscriptionId, e);
            throw new RuntimeException("Stripe Sync error: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void handleWebhookEvent(String payload, String signatureHeader) {
        log.info("Received Stripe Webhook signatureHeader: {}", signatureHeader);

        Event event;
        if (!isMockMode()) {
            try {
                event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
            } catch (Exception e) {
                log.error("Webhook signature verification failed", e);
                throw new IllegalArgumentException("Invalid signature: " + e.getMessage(), e);
            }
        } else {
            // Mock webhook constructing from JSON or parsing locally
            try {
                event = com.stripe.model.Event.GSON.fromJson(payload, com.stripe.model.Event.class);
            } catch (Exception e) {
                log.error("Failed to parse mock webhook payload", e);
                throw new IllegalArgumentException("Invalid payload format", e);
            }
        }

        String eventId = event.getId();
        log.info("Processing webhook event: id={}, type={}", eventId, event.getType());

        // Idempotency check
        if (webhookIdempotencyRepository.existsById(eventId)) {
            log.warn("Webhook event already processed: {}", eventId);
            return;
        }

        WebhookIdempotency idempotency = WebhookIdempotency.builder()
                .eventId(eventId)
                .status("RECEIVED")
                .build();
        webhookIdempotencyRepository.save(idempotency);

        try {
            Map<String, Object> eventMap = com.stripe.model.Event.GSON.fromJson(payload, Map.class);
            String eventType = event.getType();

            switch (eventType) {
                case "customer.subscription.created":
                case "customer.subscription.updated": {
                    String stripeSubId = getNestedValue(eventMap, "data", "object", "id");
                    String orgId = getNestedValue(eventMap, "data", "object", "metadata", "organizationId");
                    if (orgId == null) {
                        orgId = getNestedValue(eventMap, "data", "object", "customer");
                    }
                    List<?> items = getNestedValue(eventMap, "data", "object", "items", "data");
                    Map<?, ?> item = (items != null && !items.isEmpty()) ? (Map<?, ?>) items.get(0) : null;
                    String priceId = getNestedValue(item, "price", "id");

                    if (stripeSubId != null && orgId != null && priceId != null) {
                        BillingPlan plan = billingPlanRepository.findByStripePriceId(priceId)
                                .orElseThrow(() -> new IllegalStateException("Plan not found: " + priceId));

                        String status = getNestedValue(eventMap, "data", "object", "status");
                        if (status != null) {
                            status = status.toUpperCase();
                        } else {
                            status = "ACTIVE";
                        }

                        Number periodStartNum = getNestedValue(eventMap, "data", "object", "current_period_start");
                        Number periodEndNum = getNestedValue(eventMap, "data", "object", "current_period_end");
                        Number canceledAtNum = getNestedValue(eventMap, "data", "object", "canceled_at");

                        LocalDateTime periodStart = periodStartNum != null ?
                                LocalDateTime.ofInstant(Instant.ofEpochSecond(periodStartNum.longValue()), ZoneOffset.UTC) : LocalDateTime.now();
                        LocalDateTime periodEnd = periodEndNum != null ?
                                LocalDateTime.ofInstant(Instant.ofEpochSecond(periodEndNum.longValue()), ZoneOffset.UTC) : LocalDateTime.now().plusMonths(1);
                        LocalDateTime canceledAt = canceledAtNum != null ?
                                LocalDateTime.ofInstant(Instant.ofEpochSecond(canceledAtNum.longValue()), ZoneOffset.UTC) : null;

                        updateSubscriptionInternal(orgId, plan, stripeSubId, status, periodStart, periodEnd, canceledAt, "STRIPE_WEBHOOK");
                    }
                    break;
                }
                case "invoice.payment_succeeded": {
                    String stripeSubId = getNestedValue(eventMap, "data", "object", "subscription");
                    Number amountPaidNum = getNestedValue(eventMap, "data", "object", "amount_paid");
                    long amountPaid = amountPaidNum != null ? amountPaidNum.longValue() : 0L;
                    String currency = getNestedValue(eventMap, "data", "object", "currency");
                    if (currency != null) {
                        currency = currency.toUpperCase();
                    } else {
                        currency = "USD";
                    }
                    String invoiceId = getNestedValue(eventMap, "data", "object", "id");

                    if (stripeSubId != null) {
                        Subscription sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubId).orElse(null);
                        if (sub != null) {
                            billingService.recordSubscriptionPaymentLedger(sub.getOrganizationId(), amountPaid, currency, invoiceId);
                        }
                    }
                    break;
                }
                case "customer.subscription.deleted": {
                    String stripeSubId = getNestedValue(eventMap, "data", "object", "id");
                    if (stripeSubId != null) {
                        Subscription sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubId).orElse(null);
                        if (sub != null) {
                            updateSubscriptionInternal(
                                    sub.getOrganizationId(),
                                    sub.getPlan(),
                                    stripeSubId,
                                    "CANCELED",
                                    sub.getCurrentPeriodStart(),
                                    sub.getCurrentPeriodEnd(),
                                    LocalDateTime.now(),
                                    "STRIPE_WEBHOOK"
                            );
                        }
                    }
                    break;
                }
                default:
                    log.info("Unhandled Stripe event type: {}", eventType);
            }

            idempotency.setStatus("PROCESSED");
            webhookIdempotencyRepository.save(idempotency);
        } catch (Exception e) {
            log.error("Error processing webhook: " + eventId, e);
            idempotency.setStatus("FAILED");
            webhookIdempotencyRepository.save(idempotency);
            throw new RuntimeException("Webhook handler error", e);
        }
    }

    private void updateSubscriptionInternal(
            String orgId,
            BillingPlan plan,
            String stripeSubId,
            String status,
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime canceledAt,
            String initiator) {
        
        Subscription sub = subscriptionRepository.findByOrganizationId(orgId)
                .orElseGet(() -> Subscription.builder()
                        .organizationId(orgId)
                        .status("TRIALING")
                        .currentPeriodStart(LocalDateTime.now())
                        .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
                        .plan(plan)
                        .build());

        String fromState = sub.getStatus();
        
        sub.setPlan(plan);
        sub.setStripeSubscriptionId(stripeSubId);
        sub.setStatus(status);
        sub.setCurrentPeriodStart(start);
        sub.setCurrentPeriodEnd(end);
        sub.setCanceledAt(canceledAt);

        sub = subscriptionRepository.save(sub);

        // Record history
        SubscriptionStateHistory history = SubscriptionStateHistory.builder()
                .subscriptionId(sub.getId())
                .fromState(fromState)
                .toState(status)
                .correlationId(UUID.randomUUID().toString())
                .initiator(initiator)
                .metadata("{\"stripeSubscriptionId\":\"" + stripeSubId + "\"}")
                .build();
        stateHistoryRepository.save(history);

        // Update Quota snapshots matching plan features
        billingService.updateQuotaSnapshotsForPlan(orgId, plan);
        log.info("Subscription state transitioned from {} to {} for org {}", fromState, status, orgId);
    }

    @SuppressWarnings("unchecked")
    private <T> T getNestedValue(Map<?, ?> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(key);
        }
        return (T) current;
    }
}
