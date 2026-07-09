package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.GatewayConfig;
import com.payflow.orchestrator.domain.GatewayHealthMetricsSnapshot;
import com.payflow.orchestrator.repository.GatewayConfigRepository;
import com.payflow.orchestrator.repository.GatewayHealthMetricsSnapshotRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks gateway performance in a rolling in-memory sliding window (* "Per-minute sliding window"), falling back to the seeded historical
 * baseline at cold start.
 *
 * SCOPE: live outcomes are tracked in memory only, not persisted
 * back to gateway_health_metrics. Persisting rolled-up windows for the
 * GET /api/v1/gateways/{name}/metrics endpoint is a concern.
 *
 * "ALL" is the payment_method key for the A3.4 baseline — see
 * docs/adr/003-gateway-router-scoring.md.
 */
@Component
public class GatewayHealthMetrics {

    private static final String AGGREGATE_METHOD_KEY = "ALL";

    private final Map<String, CopyOnWriteArrayList<Outcome>> liveOutcomes = new ConcurrentHashMap<>();
    private final GatewayHealthMetricsSnapshotRepository snapshotRepository;

    private final GatewayConfigRepository gatewayConfigRepository;
    private final CircuitBreaker circuitBreaker;

    public GatewayHealthMetrics(GatewayHealthMetricsSnapshotRepository snapshotRepository,
                                GatewayConfigRepository gatewayConfigRepository,
                                CircuitBreaker circuitBreaker) {
        this.snapshotRepository = snapshotRepository;
        this.gatewayConfigRepository = gatewayConfigRepository;
        this.circuitBreaker = circuitBreaker;
    }

    public void recordOutcome(String gateway, String paymentMethod, boolean success, long latencyMs) {
        liveOutcomes.computeIfAbsent(key(gateway, paymentMethod), k -> new CopyOnWriteArrayList<>())
                .add(new Outcome(Instant.now(), success, latencyMs));

        GatewayConfig config = gatewayConfigRepository.findById(gateway)
                .orElseThrow(() -> new IllegalStateException("Unknown gateway: " + gateway));
        if (success) {
            circuitBreaker.recordSuccess(gateway, paymentMethod);
        } else {
            circuitBreaker.recordFailure(gateway, paymentMethod, config);
        }
    }

    public double successRate(String gateway, String paymentMethod, int slidingWindowMinutes) {
        List<Outcome> recent = recentOutcomes(gateway, paymentMethod, slidingWindowMinutes);
        if (!recent.isEmpty()) {
            long successCount = recent.stream().filter(Outcome::success).count();
            return (double) successCount / recent.size();
        }
        return fallbackSnapshot(gateway, paymentMethod)
                .map(GatewayHealthMetricsSnapshot::successRate)
                .orElse(1.0); // no data anywhere (brand-new gateway) — assume healthy rather than penalize unfairly
    }

    public int p95LatencyMs(String gateway, String paymentMethod, int slidingWindowMinutes) {
        List<Outcome> recent = recentOutcomes(gateway, paymentMethod, slidingWindowMinutes);
        if (!recent.isEmpty()) {
            List<Long> sorted = recent.stream().map(Outcome::latencyMs).sorted().toList();
            int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
            return sorted.get(Math.max(index, 0)).intValue();
        }
        return fallbackSnapshot(gateway, paymentMethod)
                .map(GatewayHealthMetricsSnapshot::getP95LatencyMs)
                .orElse(500); // conservative default if truly no data anywhere
    }

    public String circuitBreakerState(String gateway, String paymentMethod) {
        GatewayConfig config = gatewayConfigRepository.findById(gateway)
                .orElseThrow(() -> new IllegalStateException("Unknown gateway: " + gateway));
        return circuitBreaker.getState(gateway, paymentMethod, config).name();
    }

    private List<Outcome> recentOutcomes(String gateway, String paymentMethod, int slidingWindowMinutes) {
        Instant cutoff = Instant.now().minusSeconds(slidingWindowMinutes * 60L);
        return liveOutcomes.getOrDefault(key(gateway, paymentMethod), new CopyOnWriteArrayList<>())
                .stream()
                .filter(o -> o.timestamp().isAfter(cutoff))
                .toList();
    }

    private Optional<GatewayHealthMetricsSnapshot> fallbackSnapshot(String gateway, String paymentMethod) {
        Optional<GatewayHealthMetricsSnapshot> specific =
                snapshotRepository.findTopByGatewayAndPaymentMethodOrderByWindowStartDesc(gateway, paymentMethod);
        return specific.isPresent()
                ? specific
                : snapshotRepository.findTopByGatewayAndPaymentMethodOrderByWindowStartDesc(gateway, AGGREGATE_METHOD_KEY);
    }

    private String key(String gateway, String paymentMethod) {
        return gateway + ":" + paymentMethod;
    }

    private record Outcome(Instant timestamp, boolean success, long latencyMs) {}
}