package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.GatewayConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** No real Thread.sleep() anywhere — time is fast-forwarded via MutableClock. */
class CircuitBreakerTest {

    private static final String GATEWAY = "razorpay";
    private static final String METHOD = "CREDIT_CARD";

    // GatewayConfig.of() defaults: failureThreshold=5, timeoutSeconds=30, halfOpenMaxCalls=1
    private final GatewayConfig config = GatewayConfig.of(GATEWAY, "Razorpay", true, true, true,
            "INR_ONLY", 30, 200, new BigDecimal("0.02"), 200, 2, 2);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-08T00:00:00Z"));
    private final CircuitBreaker breaker = new CircuitBreaker(clock);

    @Test
    void startsClosed() {
        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.allowRequest(GATEWAY, METHOD, config)).isTrue();
    }

    @Test
    void staysClosedBelowFailureThreshold() {
        for (int i = 0; i < 4; i++) {
            breaker.recordFailure(GATEWAY, METHOD, config);
        }
        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    void opensAfterConsecutiveFailuresReachThreshold() {
        tripCircuitOpen();
        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.OPEN);
        assertThat(breaker.allowRequest(GATEWAY, METHOD, config)).isFalse();
    }

    @Test
    void successResetsConsecutiveFailureCount() {
        breaker.recordFailure(GATEWAY, METHOD, config);
        breaker.recordFailure(GATEWAY, METHOD, config);
        breaker.recordSuccess(GATEWAY, METHOD);
        for (int i = 0; i < 4; i++) {
            breaker.recordFailure(GATEWAY, METHOD, config); // only 4 more — should NOT reach threshold of 5
        }
        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    void transitionsToHalfOpenOnlyAfterConfiguredTimeoutElapses() {
        tripCircuitOpen();

        clock.advance(Duration.ofSeconds(29));
        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.OPEN);

        clock.advance(Duration.ofSeconds(2)); // total 31s, past the 30s threshold
        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.HALF_OPEN);
    }

    @Test
    void halfOpenAllowsExactlyOneTestRequest_thenBlocksFurtherRequests() {
        tripCircuitOpen();
        clock.advance(Duration.ofSeconds(31));

        assertThat(breaker.allowRequest(GATEWAY, METHOD, config)).isTrue();  // the one test request
        assertThat(breaker.allowRequest(GATEWAY, METHOD, config)).isFalse(); // halfOpenMaxCalls=1 already used
    }

    @Test
    void successfulTestRequestClosesTheCircuit() {
        tripCircuitOpen();
        clock.advance(Duration.ofSeconds(31));
        breaker.allowRequest(GATEWAY, METHOD, config); // consumes the test slot
        breaker.recordSuccess(GATEWAY, METHOD);

        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.allowRequest(GATEWAY, METHOD, config)).isTrue();
    }

    @Test
    void failedTestRequestReopensTheCircuitAndResetsTheTimeoutWindow() {
        tripCircuitOpen();
        clock.advance(Duration.ofSeconds(31));
        breaker.allowRequest(GATEWAY, METHOD, config); // consumes the slot, now HALF_OPEN
        breaker.recordFailure(GATEWAY, METHOD, config);

        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.OPEN);

        clock.advance(Duration.ofSeconds(5)); // only 5s after the re-open — must still be OPEN
        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.OPEN);
    }

    @Test
    void differentGatewaysAndPaymentMethodsHaveIndependentCircuitState() {
        tripCircuitOpen();
        assertThat(breaker.getState(GATEWAY, METHOD, config)).isEqualTo(CircuitState.OPEN);
        assertThat(breaker.getState(GATEWAY, "UPI", config)).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.getState("stripe", METHOD, config)).isEqualTo(CircuitState.CLOSED);
    }

    private void tripCircuitOpen() {
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure(GATEWAY, METHOD, config);
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}