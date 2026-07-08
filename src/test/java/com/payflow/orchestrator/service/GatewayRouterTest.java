package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.GatewayConfig;
import com.payflow.orchestrator.domain.RoutingConfig;
import com.payflow.orchestrator.repository.GatewayConfigRepository;
import com.payflow.orchestrator.repository.RoutingConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Known inputs, hand-calculated expected outputs. All deps mocked — no Spring context, no DB. */
class GatewayRouterTest {

    private static final String METHOD = "CREDIT_CARD";

    private GatewayConfigRepository gatewayConfigRepository;
    private RoutingConfigRepository routingConfigRepository;
    private GatewayHealthMetrics healthMetrics;
    private GatewayRouter router;

    private final RoutingConfig defaultConfig = RoutingConfig.of("default",
            new BigDecimal("0.350"), new BigDecimal("0.200"), new BigDecimal("0.200"),
            new BigDecimal("0.150"), new BigDecimal("0.100"), new BigDecimal("0.200"), 5);

    @BeforeEach
    void setUp() {
        gatewayConfigRepository = mock(GatewayConfigRepository.class);
        routingConfigRepository = mock(RoutingConfigRepository.class);
        healthMetrics = mock(GatewayHealthMetrics.class);
        router = new GatewayRouter(gatewayConfigRepository, routingConfigRepository, healthMetrics);
        when(routingConfigRepository.findById("default")).thenReturn(Optional.of(defaultConfig));
    }

    private GatewayConfig gateway(String name, String costPercentage, long costFixedPaise) {
        return GatewayConfig.of(name, name, true, true, true,
                "INR_ONLY", 30, 200, new BigDecimal(costPercentage), costFixedPaise, 2, 2);
    }

    @Test
    void higherSuccessRateAndLowerLatencyWinsOverHigherCost() {
        GatewayConfig razorpay = gateway("razorpay", "0.02000", 200);
        GatewayConfig stripe = gateway("stripe", "0.02500", 300);
        when(gatewayConfigRepository.findByEnabledTrue()).thenReturn(List.of(razorpay, stripe));

        when(healthMetrics.successRate("razorpay", METHOD, 5)).thenReturn(0.90);
        when(healthMetrics.p95LatencyMs("razorpay", METHOD, 5)).thenReturn(800);
        when(healthMetrics.circuitBreakerState("razorpay", METHOD)).thenReturn("CLOSED");

        when(healthMetrics.successRate("stripe", METHOD, 5)).thenReturn(0.99);
        when(healthMetrics.p95LatencyMs("stripe", METHOD, 5)).thenReturn(300);
        when(healthMetrics.circuitBreakerState("stripe", METHOD)).thenReturn("CLOSED");

        List<GatewayScore> scores = router.scoreEligibleGateways(METHOD, 100_000L);

        assertThat(scores).hasSize(2);
        assertThat(scores.get(0).gateway()).isEqualTo("stripe");
    }

    @Test
    void scoresSumComponentsCorrectly_knownInputsKnownOutput() {
        GatewayConfig razorpay = gateway("razorpay", "0.02000", 200);
        GatewayConfig stripe = gateway("stripe", "0.02500", 300);
        when(gatewayConfigRepository.findByEnabledTrue()).thenReturn(List.of(razorpay, stripe));

        when(healthMetrics.successRate("razorpay", METHOD, 5)).thenReturn(1.0);
        when(healthMetrics.p95LatencyMs("razorpay", METHOD, 5)).thenReturn(300); // minLatency
        when(healthMetrics.circuitBreakerState("razorpay", METHOD)).thenReturn("CLOSED");

        when(healthMetrics.successRate("stripe", METHOD, 5)).thenReturn(1.0);
        when(healthMetrics.p95LatencyMs("stripe", METHOD, 5)).thenReturn(600); // maxLatency
        when(healthMetrics.circuitBreakerState("stripe", METHOD)).thenReturn("CLOSED");

        List<GatewayScore> scores = router.scoreEligibleGateways(METHOD, 100_000L);
        GatewayScore razorpayScore = scores.stream()
                .filter(s -> s.gateway().equals("razorpay")).findFirst().orElseThrow();

        // Identical success (0.35) and health (CLOSED -> 0.15) for both.
        // Razorpay: best latency (normalized 0 -> full 0.20) AND cheapest
        // (2200 paise vs stripe's 2800 paise, normalized 0 -> full 0.20).
        // Fit is always 0.10 post-filter.
        // Expected total = 0.35 + 0.20 + 0.20 + 0.15 + 0.10 = 1.00
        assertThat(razorpayScore.totalScore()).isCloseTo(1.00, offset(0.001));
    }

    @Test
    void openCircuitBreakerExcludesGatewayEntirely() {
        GatewayConfig razorpay = gateway("razorpay", "0.02000", 200);
        GatewayConfig stripe = gateway("stripe", "0.02500", 300);
        when(gatewayConfigRepository.findByEnabledTrue()).thenReturn(List.of(razorpay, stripe));

        when(healthMetrics.successRate(anyString(), anyString(), anyInt())).thenReturn(0.95);
        when(healthMetrics.p95LatencyMs(anyString(), anyString(), anyInt())).thenReturn(400);
        when(healthMetrics.circuitBreakerState("razorpay", METHOD)).thenReturn("OPEN");
        when(healthMetrics.circuitBreakerState("stripe", METHOD)).thenReturn("CLOSED");

        List<GatewayScore> scores = router.scoreEligibleGateways(METHOD, 100_000L);

        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).gateway()).isEqualTo("stripe");
    }

    @Test
    void upiPaymentMethodOnlyRoutesToUpiGateway() {
        GatewayConfig razorpay = gateway("razorpay", "0.02000", 200);
        GatewayConfig upi = GatewayConfig.of("upi", "UPI (NPCI)", true, false, false,
                "INR_ONLY", 60, null, BigDecimal.ZERO, 0, 0, 1);
        when(gatewayConfigRepository.findByEnabledTrue()).thenReturn(List.of(razorpay, upi));
        when(healthMetrics.successRate(anyString(), anyString(), anyInt())).thenReturn(0.98);
        when(healthMetrics.p95LatencyMs(anyString(), anyString(), anyInt())).thenReturn(300);
        when(healthMetrics.circuitBreakerState(anyString(), anyString())).thenReturn("CLOSED");

        List<GatewayScore> scores = router.scoreEligibleGateways("UPI", 50_000L);

        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).gateway()).isEqualTo("upi");
    }

    @Test
    void degradedTopGatewayIsReplacedBySecondBest_whenLeadIsWithinGapThreshold() {
        GatewayConfig razorpay = gateway("razorpay", "0.02000", 200);
        GatewayConfig stripe = gateway("stripe", "0.02000", 200); // identical cost -> that component cancels out

        when(gatewayConfigRepository.findByEnabledTrue()).thenReturn(List.of(razorpay, stripe));

        when(healthMetrics.successRate("razorpay", METHOD, 5)).thenReturn(1.0);
        when(healthMetrics.p95LatencyMs("razorpay", METHOD, 5)).thenReturn(300);
        when(healthMetrics.circuitBreakerState("razorpay", METHOD)).thenReturn("HALF_OPEN");

        when(healthMetrics.successRate("stripe", METHOD, 5)).thenReturn(0.80);
        when(healthMetrics.p95LatencyMs("stripe", METHOD, 5)).thenReturn(600);
        when(healthMetrics.circuitBreakerState("stripe", METHOD)).thenReturn("CLOSED");

        List<GatewayScore> scores = router.scoreEligibleGateways(METHOD, 100_000L);
        // Hand-calculated: razorpay total = 0.925, stripe total = 0.730 (lead = 0.195)
        assertThat(scores.get(0).gateway()).isEqualTo("razorpay");
        assertThat(scores.get(0).totalScore()).isCloseTo(0.925, offset(0.001));
        assertThat(scores.get(1).totalScore()).isCloseTo(0.730, offset(0.001));

        Optional<GatewayScore> selected = router.selectBestGateway(METHOD, 100_000L);

        // razorpay is HALF_OPEN and its lead (0.195) is within the 20% gap
        // threshold -> stripe is preferred despite scoring lower raw.
        assertThat(selected).isPresent();
        assertThat(selected.get().gateway()).isEqualTo("stripe");
    }

    @Test
    void noEligibleGatewaysReturnsEmptyList() {
        when(gatewayConfigRepository.findByEnabledTrue()).thenReturn(List.of());

        assertThat(router.scoreEligibleGateways(METHOD, 100_000L)).isEmpty();
        assertThat(router.selectBestGateway(METHOD, 100_000L)).isEmpty();
    }
}