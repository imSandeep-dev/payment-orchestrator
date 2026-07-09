package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.TransactionState;
import com.payflow.orchestrator.domain.TransactionStateMachine;
import com.payflow.orchestrator.gateway.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real adapters + real TransactionStateMachine; only GatewayRouter/GatewayHealthMetrics are mocked. */
class GatewayFailoverExecutorTest {

    private GatewayRouter router;
    private GatewayHealthMetrics healthMetrics;
    private GatewayFailoverExecutor executor;

    private final UUID transactionId = UUID.randomUUID();
    private final UUID traceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        router = mock(GatewayRouter.class);
        healthMetrics = mock(GatewayHealthMetrics.class);
        List<PaymentGateway> gateways = List.of(
                new RazorpayAdapter(), new StripeAdapter(), new PayUAdapter(), new UPIAdapter());
        executor = new GatewayFailoverExecutor(router, healthMetrics, gateways, new TransactionStateMachine());
    }

    private GatewayScore scoreFor(String gateway) {
        return new GatewayScore(gateway, 1.0, 0.35, 0.2, 0.2, 0.15, 0.1, "CLOSED");
    }

    @Test
    void successOnFirstAttempt() {
        when(router.scoreEligibleGateways("UPI", 100_000L)).thenReturn(List.of(scoreFor("razorpay")));

        FailoverResult result = executor.authorizeWithFailover(transactionId, "UPI", 100_000L, traceId, Map.of());

        assertThat(result.finalState()).isEqualTo(TransactionState.AUTHORISED);
        assertThat(result.successfulGateway()).isEqualTo("razorpay");
        assertThat(result.attempts()).hasSize(1);
        verify(healthMetrics).recordOutcome(eq("razorpay"), eq("UPI"), eq(true), anyLong());
    }

    @Test
    void timeoutFailsOverToSecondGateway_withinTwoSeconds() {
        when(router.scoreEligibleGateways("CREDIT_CARD", 100_000L))
                .thenReturn(List.of(scoreFor("razorpay"), scoreFor("stripe")));

        long start = System.currentTimeMillis();
        FailoverResult result = executor.authorizeWithFailover(transactionId, "CREDIT_CARD", 100_000L, traceId,
                Map.of("razorpay", new MockInstruction(MockResponseType.TIMEOUT, 0, false)));
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(result.finalState()).isEqualTo(TransactionState.AUTHORISED);
        assertThat(result.successfulGateway()).isEqualTo("stripe");
        assertThat(result.attempts()).hasSize(2);
        assertThat(result.attempts().get(0).outcome()).isEqualTo(GatewayOutcome.TIMEOUT);
        assertThat(result.attempts().get(1).outcome()).isEqualTo(GatewayOutcome.SUCCESS);

        // Section B3's failover benchmark: < 2 seconds, primary failure to alternate success.
        assertThat(elapsedMs).isLessThan(2000);
    }

    @Test
    void declineDoesNotFailover_perSectionA11() {
        when(router.scoreEligibleGateways("CREDIT_CARD", 100_000L))
                .thenReturn(List.of(scoreFor("razorpay"), scoreFor("stripe")));

        FailoverResult result = executor.authorizeWithFailover(transactionId, "CREDIT_CARD", 100_000L, traceId,
                Map.of("razorpay", new MockInstruction(MockResponseType.DECLINE, 0, false)));

        assertThat(result.finalState()).isEqualTo(TransactionState.AUTH_FAILED);
        assertThat(result.attempts()).hasSize(1);
        verify(healthMetrics, never()).recordOutcome(eq("stripe"), any(), anyBoolean(), anyLong());
    }

    @Test
    void allGatewaysTimingOutExhaustsToFailed() {
        when(router.scoreEligibleGateways("CREDIT_CARD", 100_000L))
                .thenReturn(List.of(scoreFor("razorpay"), scoreFor("stripe"), scoreFor("payu")));

        MockInstruction timeout = new MockInstruction(MockResponseType.TIMEOUT, 0, false);
        FailoverResult result = executor.authorizeWithFailover(transactionId, "CREDIT_CARD", 100_000L, traceId,
                Map.of("razorpay", timeout, "stripe", timeout, "payu", timeout));

        assertThat(result.finalState()).isEqualTo(TransactionState.FAILED);
        assertThat(result.attempts()).hasSize(3);
        verify(healthMetrics, times(3)).recordOutcome(any(), any(), eq(false), anyLong());
    }

    @Test
    void noEligibleGatewaysFailsImmediatelyWithNoAttempts() {
        when(router.scoreEligibleGateways("CREDIT_CARD", 100_000L)).thenReturn(List.of());

        FailoverResult result = executor.authorizeWithFailover(transactionId, "CREDIT_CARD", 100_000L, traceId, Map.of());

        assertThat(result.finalState()).isEqualTo(TransactionState.FAILED);
        assertThat(result.attempts()).isEmpty();
        verifyNoInteractions(healthMetrics);
    }

    @Test
    void upiMandateExpiryDoesNotFailover_perFS12() {
        when(router.scoreEligibleGateways("UPI", 50_000L)).thenReturn(List.of(scoreFor("upi")));

        FailoverResult result = executor.authorizeWithFailover(transactionId, "UPI", 50_000L, traceId,
                Map.of("upi", new MockInstruction(MockResponseType.TIMEOUT, 0, false)));

        assertThat(result.finalState()).isEqualTo(TransactionState.AUTH_EXPIRED);
        assertThat(result.finalState().isTerminal()).isTrue();
        assertThat(result.attempts()).hasSize(1);
        assertThat(result.attempts().get(0).outcome()).isEqualTo(GatewayOutcome.MANDATE_EXPIRED);
    }
}