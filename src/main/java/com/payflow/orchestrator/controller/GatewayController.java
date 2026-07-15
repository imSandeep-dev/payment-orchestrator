package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.GatewayConfig;
import com.payflow.orchestrator.exception.ApiException;
import com.payflow.orchestrator.repository.GatewayConfigRepository;
import com.payflow.orchestrator.service.GatewayHealthMetrics;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gateways")
public class GatewayController {

    private static final String AGGREGATE_METHOD = "ALL"; // Day 7 convention
    private static final int DEFAULT_WINDOW_MINUTES = 5;

    private final GatewayConfigRepository gatewayConfigRepository;
    private final GatewayHealthMetrics healthMetrics;

    public GatewayController(GatewayConfigRepository gatewayConfigRepository, GatewayHealthMetrics healthMetrics) {
        this.gatewayConfigRepository = gatewayConfigRepository;
        this.healthMetrics = healthMetrics;
    }

    public record GatewaySummary(String gatewayName, String displayName, boolean enabled, String circuitState) {}

    @GetMapping
    public List<GatewaySummary> list() {
        return gatewayConfigRepository.findAll().stream()
                .map(g -> new GatewaySummary(g.getGatewayName(), g.getDisplayName(), g.isEnabled(),
                        healthMetrics.circuitBreakerState(g.getGatewayName(), AGGREGATE_METHOD)))
                .toList();
    }

    public record GatewayHealth(String gateway, String circuitState, double successRate) {}

    @GetMapping("/{name}/health")
    public GatewayHealth health(@PathVariable String name) {
        requireExists(name);
        return new GatewayHealth(name, healthMetrics.circuitBreakerState(name, AGGREGATE_METHOD),
                healthMetrics.successRate(name, AGGREGATE_METHOD, DEFAULT_WINDOW_MINUTES));
    }

    public record GatewayMetrics(String gateway, double successRate, int p95LatencyMs, String circuitState) {}

    @GetMapping("/{name}/metrics")
    public GatewayMetrics metrics(@PathVariable String name) {
        requireExists(name);
        return new GatewayMetrics(name, healthMetrics.successRate(name, AGGREGATE_METHOD, DEFAULT_WINDOW_MINUTES),
                healthMetrics.p95LatencyMs(name, AGGREGATE_METHOD, DEFAULT_WINDOW_MINUTES),
                healthMetrics.circuitBreakerState(name, AGGREGATE_METHOD));
    }

    public record UpdateGatewayConfigRequest(
            @NotNull Boolean enabled,
            @DecimalMin("0.0") BigDecimal costPercentage,
            @PositiveOrZero long costFixedPaise,
            Integer rateLimitPerSecond,
            @NotNull Integer circuitBreakerFailureThreshold,
            @NotNull Integer circuitBreakerTimeoutSeconds,
            @NotNull Integer circuitBreakerHalfOpenMaxCalls) {}

    @PutMapping("/{name}/config")
    public GatewaySummary updateConfig(@PathVariable String name, @Valid @RequestBody UpdateGatewayConfigRequest req) {
        GatewayConfig config = requireExists(name);
        config.applyUpdate(req.enabled(), req.costPercentage(), req.costFixedPaise(), req.rateLimitPerSecond(),
                req.circuitBreakerFailureThreshold(), req.circuitBreakerTimeoutSeconds(), req.circuitBreakerHalfOpenMaxCalls());
        GatewayConfig saved = gatewayConfigRepository.save(config);
        return new GatewaySummary(saved.getGatewayName(), saved.getDisplayName(), saved.isEnabled(),
                healthMetrics.circuitBreakerState(name, AGGREGATE_METHOD));
    }

    private GatewayConfig requireExists(String name) {
        return gatewayConfigRepository.findById(name).orElseThrow(() -> ApiException.gatewayNotFound(name));
    }
}