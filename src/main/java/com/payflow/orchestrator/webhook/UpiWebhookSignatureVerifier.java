package com.payflow.orchestrator.webhook;

import org.springframework.stereotype.Component;

/**
 * SIMPLIFICATION NOTE: specifies UPI uses an NPCI-issued
 * digital certificate (asymmetric public-key signature), not a shared-secret
 * HMAC. A faithful implementation would use java.security.Signature with
 * RSA/ECDSA and X.509 certificate parsing/validation — a significant
 * undertaking with no real NPCI certificate available in a training/mock
 * environment. This adapter uses HMAC-SHA256 as a structural stand-in so
 * the pipeline's shape (verify -> dedup -> process) is fully exercisable
 * end-to-end. See docs/adr/006-webhook-pipeline-design.md.
 */
@Component
public class UpiWebhookSignatureVerifier extends AbstractHmacWebhookSignatureVerifier {

    public UpiWebhookSignatureVerifier(WebhookSecretsProperties secrets) {
        super(secrets.secretFor("upi"));
    }

    @Override
    public String getGatewayName() {
        return "upi";
    }

    @Override
    protected String getAlgorithm() {
        return "HmacSHA256";
    }
}