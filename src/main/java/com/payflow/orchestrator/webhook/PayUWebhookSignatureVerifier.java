package com.payflow.orchestrator.webhook;

import org.springframework.stereotype.Component;

@Component
public class PayUWebhookSignatureVerifier extends AbstractHmacWebhookSignatureVerifier {

    public PayUWebhookSignatureVerifier(WebhookSecretsProperties secrets) {
        super(secrets.secretFor("payu"));
    }

    @Override
    public String getGatewayName() {
        return "payu";
    }

    @Override
    protected String getAlgorithm() {
        return "HmacSHA512";
    }
}