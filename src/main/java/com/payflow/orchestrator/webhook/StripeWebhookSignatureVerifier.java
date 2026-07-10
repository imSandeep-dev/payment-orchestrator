package com.payflow.orchestrator.webhook;

import com.payflow.orchestrator.util.HmacUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Stripe uses HMAC-SHA256 via the Stripe-Signature header,
 * format "t=<timestamp>,v1=<hex signature>" — signed payload is
 * "<timestamp>.<rawBody>", mirroring Stripe's real documented scheme.
 * Doesn't extend AbstractHmacWebhookSignatureVerifier because the signed
 * payload isn't the raw body alone; header parsing is genuinely different.
 */
@Component
public class StripeWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private final String secret;

    public StripeWebhookSignatureVerifier(WebhookSecretsProperties secrets) {
        this.secret = secrets.secretFor("stripe");
    }

    @Override
    public String getGatewayName() {
        return "stripe";
    }

    @Override
    public boolean verify(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        Map<String, String> parts = parseHeader(signatureHeader);
        String timestamp = parts.get("t");
        String providedSignature = parts.get("v1");
        if (timestamp == null || providedSignature == null) {
            return false;
        }
        String signedPayload = timestamp + "." + rawBody;
        String expected = HmacUtil.hmacHex("HmacSHA256", secret, signedPayload);
        return HmacUtil.constantTimeEquals(expected, providedSignature);
    }

    private Map<String, String> parseHeader(String header) {
        Map<String, String> result = new HashMap<>();
        for (String segment : header.split(",")) {
            String[] kv = segment.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        return result;
    }
}