package com.payflow.orchestrator.gateway;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared behavioral contract every mock adapter must satisfy. Concrete
 * adapter test classes just supply the instance under test — this
 * guarantees Razorpay/Stripe/PayU/UPI can never silently drift out of sync.
 */
public abstract class PaymentGatewayContractTest {

    protected abstract PaymentGateway gateway();

    private AuthorizationRequest authRequest(MockInstruction instruction) {
        return new AuthorizationRequest(UUID.randomUUID(), 100000L, "INR", "UPI",
                UUID.randomUUID(), instruction);
    }

    @Test
    void successInstructionReturnsSuccessWithGatewayReference() {
        GatewayResult result = gateway().authorize(
                authRequest(new MockInstruction(MockResponseType.SUCCESS, 0, false)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.gatewayReference()).isNotBlank().startsWith(gateway().getGatewayName());
        assertThat(result.rawResponseJson()).contains("\"status\":\"success\"");
    }

    @Test
    void declineInstructionReturnsDeclinedWithNoGatewayReference() {
        GatewayResult result = gateway().authorize(
                authRequest(new MockInstruction(MockResponseType.DECLINE, 0, false)));

        assertThat(result.outcome()).isEqualTo(GatewayOutcome.DECLINED);
        assertThat(result.gatewayReference()).isNull();
        assertThat(result.errorMessage()).isNotBlank();
    }

    @Test
    void serverErrorInstructionReturnsServerError() {
        GatewayResult result = gateway().authorize(
                authRequest(new MockInstruction(MockResponseType.SERVER_ERROR, 0, false)));

        assertThat(result.outcome()).isEqualTo(GatewayOutcome.SERVER_ERROR);
        assertThat(result.gatewayReference()).isNull();
    }

    @Test
    void rateLimitInstructionReturnsRateLimitedWithRetryAfter() {
        GatewayResult result = gateway().authorize(
                authRequest(new MockInstruction(MockResponseType.RATE_LIMIT, 0, false)));

        assertThat(result.outcome()).isEqualTo(GatewayOutcome.RATE_LIMITED);
        assertThat(result.retryAfterSeconds()).isNotNull().isPositive();
    }

    @Test
    void timeoutInstructionReturnsTimeoutPromptly() {
        long start = System.currentTimeMillis();

        GatewayResult result = gateway().authorize(
                authRequest(new MockInstruction(MockResponseType.TIMEOUT, 0, false)));

        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(result.outcome()).isEqualTo(GatewayOutcome.TIMEOUT);
        // See ADR-002: we do NOT block for the real 30-60s window.
        assertThat(elapsedMs).isLessThan(500);
    }

    @Test
    void gatewayDownInstructionOverridesResponseType_returningServerError() {
        MockInstruction gatewayDown = new MockInstruction(MockResponseType.SUCCESS, 0, true);

        GatewayResult result = gateway().authorize(authRequest(gatewayDown));

        assertThat(result.outcome()).isEqualTo(GatewayOutcome.SERVER_ERROR);
        assertThat(result.errorMessage()).contains("unreachable");
    }

    @Test
    void configuredDelayIsActuallyApplied() {
        long start = System.currentTimeMillis();

        gateway().authorize(authRequest(new MockInstruction(MockResponseType.SUCCESS, 300, false)));

        long elapsedMs = System.currentTimeMillis() - start;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
    }

    @Test
    void gatewayNameIsLowercase_matchingGatewayConfigSeedData() {
        // Guards against a typo mismatching the gateway_config table (Day 2, V11).
        assertThat(gateway().getGatewayName()).isEqualTo(gateway().getGatewayName().toLowerCase());
    }
}