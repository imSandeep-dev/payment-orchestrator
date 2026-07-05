package com.payflow.orchestrator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implements API endpoint #23 from Section A7.1: GET /api/v1/health.
 *
 * TODO (Day 7-8): include per-gateway circuit breaker states once
 *                 GatewayHealthMetrics service exists.
 * TODO (Day 9-10): include webhook dead-letter-queue depth once
 *                 the webhook_queue table/repository exists.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        boolean dbUp = isDatabaseReachable();

        Map<String, String> components = new LinkedHashMap<>();
        components.put("database", dbUp ? "UP" : "DOWN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("service", "payment-orchestrator");
        body.put("timestamp", Instant.now().toString());
        body.put("components", components);
        return body;
    }

    private boolean isDatabaseReachable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result);
        } catch (Exception ex) {
            return false;
        }
    }
}