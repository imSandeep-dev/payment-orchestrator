package com.payflow.orchestrator.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * DEV/MOCK ONLY. gateway_config.webhook_secret_ref is the
 * intended direction point for a real secrets manager — swapping this
 * class's internals for one is a drop-in replacement, not a redesign.
 */
@Component
@ConfigurationProperties(prefix = "app.webhook-secrets")
public class WebhookSecretsProperties {

    private Map<String, String> gateways = new HashMap<>();

    public Map<String, String> getGateways() {
        return gateways;
    }

    public void setGateways(Map<String, String> gateways) {
        this.gateways = gateways;
    }

    public String secretFor(String gateway) {
        String secret = gateways.get(gateway);
        if (secret == null) {
            throw new IllegalStateException("No webhook secret configured for gateway: " + gateway);
        }
        return secret;
    }
}