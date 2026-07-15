package com.payflow.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "app.api-keys")
public class ApiKeyProperties {

    private Map<String, String> keys = new HashMap<>();

    public Map<String, String> getKeys() { return keys; }
    public void setKeys(Map<String, String> keys) { this.keys = keys; }

    public Optional<String> merchantIdFor(String apiKey) {
        return Optional.ofNullable(keys.get(apiKey));
    }
}