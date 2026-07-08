package com.payflow.orchestrator.domain;

import com.payflow.orchestrator.gateway.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the full chain works end-to-end: a mock gateway's response ->
 * GatewayOutcomeMapper -> a real TransactionStateMachine transition.
 * No Spring context, no database — pure object wiring, milliseconds to run.
 */
class GatewayToStateMachineIntegrationTest {

    private final TransactionStateMachine stateMachine = new TransactionStateMachine();

    @Test
    void successfulRazorpayAuthorizationMovesAuthInitiatedToAuthorised() {
        PaymentGateway razorpay = new RazorpayAdapter();
        AuthorizationRequest request = new AuthorizationRequest(UUID.randomUUID(), 100000L, "INR",
                "UPI", UUID.randomUUID(), MockInstruction.success());

        GatewayResult result = razorpay.authorize(request);
        TransactionEvent event = GatewayOutcomeMapper.forAuthorize(result.outcome());
        TransactionState nextState = stateMachine.transition(TransactionState.AUTH_INITIATED, event);

        assertThat(nextState).isEqualTo(TransactionState.AUTHORISED);
    }

    @Test
    void declinedStripeAuthorizationMovesAuthInitiatedToAuthFailed() {
        PaymentGateway stripe = new StripeAdapter();
        AuthorizationRequest request = new AuthorizationRequest(UUID.randomUUID(), 250000L, "INR",
                "CREDIT_CARD", UUID.randomUUID(), new MockInstruction(MockResponseType.DECLINE, 0, false));

        GatewayResult result = stripe.authorize(request);
        TransactionEvent event = GatewayOutcomeMapper.forAuthorize(result.outcome());
        TransactionState nextState = stateMachine.transition(TransactionState.AUTH_INITIATED, event);

        assertThat(nextState).isEqualTo(TransactionState.AUTH_FAILED);
    }

    @Test
    void payuServerErrorMovesAuthInitiatedToAuthTimeout_eligibleForFailover() {
        PaymentGateway payu = new PayUAdapter();
        AuthorizationRequest request = new AuthorizationRequest(UUID.randomUUID(), 50000L, "INR",
                "NETBANKING", UUID.randomUUID(), new MockInstruction(MockResponseType.SERVER_ERROR, 0, false));

        GatewayResult result = payu.authorize(request);
        TransactionEvent event = GatewayOutcomeMapper.forAuthorize(result.outcome());
        TransactionState nextState = stateMachine.transition(TransactionState.AUTH_INITIATED, event);

        assertThat(nextState).isEqualTo(TransactionState.AUTH_TIMEOUT);
        TransactionState afterFailover = stateMachine.transition(nextState, TransactionEvent.FAILOVER);
        assertThat(afterFailover).isEqualTo(TransactionState.ROUTE_SELECTED);
    }

    @Test
    void upiMandateExpiryMovesAuthInitiatedDirectlyToAuthExpired_perFS12() {
        PaymentGateway upi = new UPIAdapter();
        AuthorizationRequest request = new AuthorizationRequest(UUID.randomUUID(), 30000L, "INR",
                "UPI", UUID.randomUUID(), new MockInstruction(MockResponseType.TIMEOUT, 0, false));

        GatewayResult result = upi.authorize(request);
        TransactionEvent event = GatewayOutcomeMapper.forAuthorize(result.outcome());
        TransactionState nextState = stateMachine.transition(TransactionState.AUTH_INITIATED, event);

        assertThat(nextState).isEqualTo(TransactionState.AUTH_EXPIRED);
        assertThat(nextState.isTerminal()).isTrue();
    }

    @Test
    void rateLimitedResponseAlsoConsolidatesOntoAuthTimeout() {
        PaymentGateway stripe = new StripeAdapter();
        AuthorizationRequest request = new AuthorizationRequest(UUID.randomUUID(), 400000L, "INR",
                "CREDIT_CARD", UUID.randomUUID(), new MockInstruction(MockResponseType.RATE_LIMIT, 0, false));

        GatewayResult result = stripe.authorize(request);
        TransactionEvent event = GatewayOutcomeMapper.forAuthorize(result.outcome());
        TransactionState nextState = stateMachine.transition(TransactionState.AUTH_INITIATED, event);

        assertThat(nextState).isEqualTo(TransactionState.AUTH_TIMEOUT);
    }
}