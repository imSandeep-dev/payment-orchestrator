package com.payflow.orchestrator.webhook;

public record IncomingWebhookRequest(
        String gateway, String eventId, WebhookEventType eventType, String gatewayReference,
        long amountPaise, String currency, String rawBody, String signatureHeader
) {}