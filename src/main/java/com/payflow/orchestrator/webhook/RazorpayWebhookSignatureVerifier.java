package com.payflow.orchestrator.webhook;

import org.springframework.stereotype.Component;

@Component
public class RazorpayWebhookSignatureVerifier extends AbstractHmacWebhookSignatureVerifier {

    public RazorpayWebhookSignatureVerifier(WebhookSecretsProperties secrets) {
        super(secrets.secretFor("razorpay"));
    }

    @Override
    public String getGatewayName() {
        return "razorpay";
    }

    @Override
    protected String getAlgorithm() {
        return "HmacSHA256";
    }
}