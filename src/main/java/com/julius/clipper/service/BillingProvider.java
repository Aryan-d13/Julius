package com.julius.clipper.service;

public interface BillingProvider {
    /**
     * Creates a Stripe Checkout Session URL for subscription onboarding.
     */
    String createCheckoutSession(String organizationId, String priceId, String successUrl, String cancelUrl);

    /**
     * Creates a Stripe Customer Portal Session URL for subscription management.
     */
    String createCustomerPortalSession(String organizationId, String returnUrl);

    /**
     * Synchronizes a subscription state from Stripe into the local database.
     */
    void syncSubscription(String stripeSubscriptionId);

    /**
     * Handles webhook payload parsing and signature verification securely.
     */
    void handleWebhookEvent(String payload, String signatureHeader);
}
