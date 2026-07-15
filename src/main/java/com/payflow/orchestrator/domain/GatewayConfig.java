package com.payflow.orchestrator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "gateway_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatewayConfig {

    @Id
    @Column(name = "gateway_name", length = 50)
    private String gatewayName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "supports_auth_capture", nullable = false)
    private boolean supportsAuthCapture;

    @Column(name = "supports_partial_refund", nullable = false)
    private boolean supportsPartialRefund;

    @Column(name = "currency_support", nullable = false)
    private String currencySupport;

    @Column(name = "auth_timeout_seconds", nullable = false)
    private int authTimeoutSeconds;

    @Column(name = "rate_limit_per_second")
    private Integer rateLimitPerSecond;

    @Column(name = "cost_percentage", nullable = false)
    private BigDecimal costPercentage;

    @Column(name = "cost_fixed_paise", nullable = false)
    private long costFixedPaise;

    @Column(name = "settlement_days_min", nullable = false)
    private int settlementDaysMin;

    @Column(name = "settlement_days_max", nullable = false)
    private int settlementDaysMax;

    @Column(name = "circuit_breaker_failure_threshold", nullable = false)
    private int circuitBreakerFailureThreshold;

    @Column(name = "circuit_breaker_timeout_seconds", nullable = false)
    private int circuitBreakerTimeoutSeconds;

    @Column(name = "circuit_breaker_half_open_max_calls", nullable = false)
    private int circuitBreakerHalfOpenMaxCalls;

    @Column(name = "webhook_secret_ref")
    private String webhookSecretRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Section A3.1's "Cost" factor data source: percentage fee and fixed fee, in paise. */
    public long estimateCostPaise(long amountPaise) {
        BigDecimal percentageFee = costPercentage.multiply(BigDecimal.valueOf(amountPaise));
        return percentageFee.longValue() + costFixedPaise;
    }

    /** Test/programmatic construction — this entity has no setters, the same discipline as Transaction (Day 4). */
    public static GatewayConfig of(String gatewayName, String displayName, boolean enabled,
                                   boolean supportsAuthCapture, boolean supportsPartialRefund,
                                   String currencySupport, int authTimeoutSeconds, Integer rateLimitPerSecond,
                                   BigDecimal costPercentage, long costFixedPaise,
                                   int settlementDaysMin, int settlementDaysMax) {
        GatewayConfig g = new GatewayConfig();
        g.gatewayName = gatewayName;
        g.displayName = displayName;
        g.enabled = enabled;
        g.supportsAuthCapture = supportsAuthCapture;
        g.supportsPartialRefund = supportsPartialRefund;
        g.currencySupport = currencySupport;
        g.authTimeoutSeconds = authTimeoutSeconds;
        g.rateLimitPerSecond = rateLimitPerSecond;
        g.costPercentage = costPercentage;
        g.costFixedPaise = costFixedPaise;
        g.settlementDaysMin = settlementDaysMin;
        g.settlementDaysMax = settlementDaysMax;
        g.circuitBreakerFailureThreshold = 5;
        g.circuitBreakerTimeoutSeconds = 30;
        g.circuitBreakerHalfOpenMaxCalls = 1;
        Instant now = Instant.now();
        g.createdAt = now;
        g.updatedAt = now;
        return g;
    }

    public void applyUpdate(boolean enabled, BigDecimal costPercentage, long costFixedPaise, Integer rateLimitPerSecond,
                            int circuitBreakerFailureThreshold, int circuitBreakerTimeoutSeconds, int circuitBreakerHalfOpenMaxCalls) {
        this.enabled = enabled;
        this.costPercentage = costPercentage;
        this.costFixedPaise = costFixedPaise;
        this.rateLimitPerSecond = rateLimitPerSecond;
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
        this.circuitBreakerTimeoutSeconds = circuitBreakerTimeoutSeconds;
        this.circuitBreakerHalfOpenMaxCalls = circuitBreakerHalfOpenMaxCalls;
    }

    @PreUpdate
    void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}