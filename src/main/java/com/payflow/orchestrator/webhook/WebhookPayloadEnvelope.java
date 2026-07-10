package com.payflow.orchestrator.webhook;

/**
 * Our own canonical webhook body shape for the mock/training environment
 * (real Razorpay/Stripe/PayU/UPI payloads have their own distinct native
 * JSON shapes and event-name vocabularies — translating this is out of
 * scope here since we're testing against our own mock gateways, not real
 * ones; see ADR-007).
 */
public record WebhookPayloadEnvelope(String eventId, String eventType, String gatewayReference,
                                     long amountPaise, String currency) {}