package com.payflow.orchestrator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Maps gateway_health_metrics. Uses a DB-generated Long id
 * (GenerationType.IDENTITY), unlike our usual app-generated UUID
 * convention — this table is high-volume telemetry, not a core financial
 * record, so it doesn't need the same identity-generation discipline.
 */
@Entity
@Table(name = "gateway_health_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatewayHealthMetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway", nullable = false, length = 50)
    private String gateway;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "p95_latency_ms")
    private Integer p95LatencyMs;

    @Column(name = "circuit_breaker_state", nullable = false, length = 20)
    private String circuitBreakerState;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "circuit_opened_at")
    private Instant circuitOpenedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public double successRate() {
        return totalCount == 0 ? 1.0 : (double) successCount / totalCount;
    }
}