package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.GatewayConfig;
import com.payflow.orchestrator.domain.RoutingConfig;
import com.payflow.orchestrator.repository.GatewayConfigRepository;
import com.payflow.orchestrator.repository.RoutingConfigRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implements the multi-criteria gateway scoring formula.
 * Scope: scoring and selection given CURRENT health-metric inputs.
 * Adds CircuitBreaker, which actually maintains those inputs over
 * time, and the router-level failover retry loop.
 */
@Component
public class GatewayRouter {

    private final GatewayConfigRepository gatewayConfigRepository;
    private final RoutingConfigRepository routingConfigRepository;
    private final GatewayHealthMetrics healthMetrics;

    public GatewayRouter(GatewayConfigRepository gatewayConfigRepository,
                         RoutingConfigRepository routingConfigRepository,
                         GatewayHealthMetrics healthMetrics) {
        this.gatewayConfigRepository = gatewayConfigRepository;
        this.routingConfigRepository = routingConfigRepository;
        this.healthMetrics = healthMetrics;
    }

    /**
     * Scores every enabled, payment-method-compatible, non-OPEN-circuit
     * gateway, sorted best-first. Empty list means no eligible gateway
     * exists (-> ROUTE_FAILED, see docs/state-machine.md).
     */
    public List<GatewayScore> scoreEligibleGateways(String paymentMethod, long amountPaise) {
        return scoreEligibleGateways(paymentMethod, amountPaise, currentRoutingConfig());
    }

    /**
     * Applies A3.2's degraded-gateway preference: if the top-scoring
     * gateway is HALF_OPEN ("degraded"), prefers the second-best UNLESS the
     * top gateway's score lead exceeds the configured gap threshold
     * (default 20%).
     */
    public Optional<GatewayScore> selectBestGateway(String paymentMethod, long amountPaise) {
        RoutingConfig config = currentRoutingConfig();
        List<GatewayScore> scored = scoreEligibleGateways(paymentMethod, amountPaise, config);

        if (scored.isEmpty()) {
            return Optional.empty();
        }
        if (scored.size() == 1) {
            return Optional.of(scored.get(0));
        }

        GatewayScore best = scored.get(0);
        GatewayScore second = scored.get(1);
        boolean bestIsDegraded = "HALF_OPEN".equals(best.circuitBreakerState());
        double scoreLead = best.totalScore() - second.totalScore();

        if (bestIsDegraded && scoreLead <= config.getDegradedScoreGapThreshold().doubleValue()) {
            return Optional.of(second);
        }
        return Optional.of(best);
    }

    private List<GatewayScore> scoreEligibleGateways(String paymentMethod, long amountPaise, RoutingConfig config) {
        List<GatewayConfig> candidates = gatewayConfigRepository.findByEnabledTrue().stream()
                .filter(g -> supportsPaymentMethod(g, paymentMethod))
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        // OPEN circuits are excluded BEFORE computing the normalization
        // baseline (see ADR-003 #2) — an excluded gateway's numbers must
        // not skew the min-max range for the gateways still competing.
        List<RawMetrics> raw = candidates.stream()
                .map(g -> rawMetricsFor(g, paymentMethod, config.getSlidingWindowMinutes()))
                .filter(r -> !"OPEN".equals(r.circuitBreakerState))
                .toList();

        if (raw.isEmpty()) {
            return List.of();
        }

        double minLatency = raw.stream().mapToDouble(RawMetrics::p95LatencyMs).min().orElse(0);
        double maxLatency = raw.stream().mapToDouble(RawMetrics::p95LatencyMs).max().orElse(1);
        double minCost = raw.stream().mapToDouble(r -> r.gatewayConfig.estimateCostPaise(amountPaise)).min().orElse(0);
        double maxCost = raw.stream().mapToDouble(r -> r.gatewayConfig.estimateCostPaise(amountPaise)).max().orElse(1);

        return raw.stream()
                .map(r -> score(r, config, amountPaise, minLatency, maxLatency, minCost, maxCost))
                .sorted(Comparator.comparingDouble(GatewayScore::totalScore).reversed())
                .toList();
    }

    private boolean supportsPaymentMethod(GatewayConfig gateway, String paymentMethod) {
        // UPI is exclusively served by the UPI gateway (A1.3) — a hard
        // filter, not a soft FitScore contribution (see ADR-003 #3).
        if ("UPI".equals(paymentMethod)) {
            return "upi".equals(gateway.getGatewayName());
        }
        return !"upi".equals(gateway.getGatewayName());
    }

    private RawMetrics rawMetricsFor(GatewayConfig gateway, String paymentMethod, int slidingWindowMinutes) {
        double successRate = healthMetrics.successRate(gateway.getGatewayName(), paymentMethod, slidingWindowMinutes);
        int p95Latency = healthMetrics.p95LatencyMs(gateway.getGatewayName(), paymentMethod, slidingWindowMinutes);
        String circuitState = healthMetrics.circuitBreakerState(gateway.getGatewayName(), paymentMethod);
        return new RawMetrics(gateway, successRate, p95Latency, circuitState);
    }

    private GatewayScore score(RawMetrics r, RoutingConfig config, long amountPaise,
                               double minLatency, double maxLatency, double minCost, double maxCost) {
        double normalizedLatency = normalize(r.p95LatencyMs, minLatency, maxLatency);
        double cost = r.gatewayConfig.estimateCostPaise(amountPaise);
        double normalizedCost = normalize(cost, minCost, maxCost);
        double healthScore = switch (r.circuitBreakerState) {
            case "CLOSED" -> 1.0;
            case "HALF_OPEN" -> 0.5;
            default -> 0.0; // OPEN already filtered out — defensive default only
        };
        double fitScore = 1.0; // already filtered to fit-compatible gateways

        double successComponent = config.getWeightSuccessRate().doubleValue() * r.successRate;
        double latencyComponent = config.getWeightLatency().doubleValue() * (1 - normalizedLatency);
        double costComponent = config.getWeightCost().doubleValue() * (1 - normalizedCost);
        double healthComponent = config.getWeightHealth().doubleValue() * healthScore;
        double fitComponent = config.getWeightPaymentMethodFit().doubleValue() * fitScore;

        double total = successComponent + latencyComponent + costComponent + healthComponent + fitComponent;

        return new GatewayScore(r.gatewayConfig.getGatewayName(), total,
                successComponent, latencyComponent, costComponent, healthComponent, fitComponent,
                r.circuitBreakerState);
    }

    private double normalize(double value, double min, double max) {
        return (max - min == 0) ? 0 : (value - min) / (max - min);
    }

    private RoutingConfig currentRoutingConfig() {
        return routingConfigRepository.findById("default")
                .orElseThrow(() -> new IllegalStateException("routing_config 'default' row missing — check V11 seed migration"));
    }

    private record RawMetrics(GatewayConfig gatewayConfig, double successRate, int p95LatencyMs, String circuitBreakerState) {}
}