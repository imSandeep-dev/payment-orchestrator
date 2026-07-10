package com.payflow.orchestrator.webhook;

public interface WebhookSignatureVerifier {
    String getGatewayName();
    boolean verify(String rawBody, String signatureHeader);
}