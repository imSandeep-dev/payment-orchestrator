package com.payflow.orchestrator.webhook;

import com.payflow.orchestrator.util.HmacUtil;

public abstract class AbstractHmacWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private final String secret;

    protected AbstractHmacWebhookSignatureVerifier(String secret) {
        this.secret = secret;
    }

    @Override
    public boolean verify(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String expected = HmacUtil.hmacHex(getAlgorithm(), secret, rawBody);
        return HmacUtil.constantTimeEquals(expected, signatureHeader);
    }

    protected abstract String getAlgorithm();
}