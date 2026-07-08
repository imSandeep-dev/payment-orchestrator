package com.payflow.orchestrator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Maps routing_config (Day 2, V1/V11): configurable weights for the A3.2 scoring formula. */
@Entity
@Table(name = "routing_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutingConfig {

    @Id
    @Column(name = "config_key", length = 100)
    private String configKey;

    @Column(name = "weight_success_rate", nullable = false)
    private BigDecimal weightSuccessRate;

    @Column(name = "weight_latency", nullable = false)
    private BigDecimal weightLatency;

    @Column(name = "weight_cost", nullable = false)
    private BigDecimal weightCost;

    @Column(name = "weight_health", nullable = false)
    private BigDecimal weightHealth;

    @Column(name = "weight_payment_method_fit", nullable = false)
    private BigDecimal weightPaymentMethodFit;

    @Column(name = "degraded_score_gap_threshold", nullable = false)
    private BigDecimal degradedScoreGapThreshold;

    @Column(name = "sliding_window_minutes", nullable = false)
    private int slidingWindowMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static RoutingConfig of(String configKey, BigDecimal weightSuccessRate, BigDecimal weightLatency,
                                   BigDecimal weightCost, BigDecimal weightHealth, BigDecimal weightPaymentMethodFit,
                                   BigDecimal degradedScoreGapThreshold, int slidingWindowMinutes) {
        RoutingConfig r = new RoutingConfig();
        r.configKey = configKey;
        r.weightSuccessRate = weightSuccessRate;
        r.weightLatency = weightLatency;
        r.weightCost = weightCost;
        r.weightHealth = weightHealth;
        r.weightPaymentMethodFit = weightPaymentMethodFit;
        r.degradedScoreGapThreshold = degradedScoreGapThreshold;
        r.slidingWindowMinutes = slidingWindowMinutes;
        Instant now = Instant.now();
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }
}