package com.payflow.orchestrator.webhook;


public enum WebhookEventType {
    PAYMENT_CAPTURED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,
    PAYMENT_PARTIALLY_REFUNDED,
    PAYMENT_REVERSED,
    PAYMENT_SETTLED,
    DISPUTE_CREATED
}