package com.payflow.orchestrator.domain;

import com.payflow.orchestrator.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionStateMachineTest {

    private final TransactionStateMachine stateMachine = new TransactionStateMachine();

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("validTransitions")
    void validTransitionsSucceed(TransactionState from, TransactionEvent event, TransactionState expectedTo) {
        assertThat(stateMachine.transition(from, event)).isEqualTo(expectedTo);
        assertThat(stateMachine.canTransition(from, event)).isTrue();
    }

    static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(TransactionState.CREATED, TransactionEvent.ROUTE_SELECTED, TransactionState.ROUTE_SELECTED),
                Arguments.of(TransactionState.CREATED, TransactionEvent.ABANDONED, TransactionState.ABANDONED),
                Arguments.of(TransactionState.ROUTE_SELECTED, TransactionEvent.AUTH_INITIATED, TransactionState.AUTH_INITIATED),
                Arguments.of(TransactionState.ROUTE_SELECTED, TransactionEvent.ROUTE_FAILED, TransactionState.ROUTE_FAILED),
                Arguments.of(TransactionState.ROUTE_FAILED, TransactionEvent.RETRY_ROUTE, TransactionState.ROUTE_SELECTED),
                Arguments.of(TransactionState.ROUTE_FAILED, TransactionEvent.EXHAUSTED, TransactionState.FAILED),
                Arguments.of(TransactionState.AUTH_INITIATED, TransactionEvent.GATEWAY_AUTH_SUCCESS, TransactionState.AUTHORISED),
                Arguments.of(TransactionState.AUTH_INITIATED, TransactionEvent.GATEWAY_AUTH_DECLINE, TransactionState.AUTH_FAILED),
                Arguments.of(TransactionState.AUTH_INITIATED, TransactionEvent.GATEWAY_TIMEOUT, TransactionState.AUTH_TIMEOUT),
                Arguments.of(TransactionState.AUTH_TIMEOUT, TransactionEvent.FAILOVER, TransactionState.ROUTE_SELECTED),
                Arguments.of(TransactionState.AUTH_TIMEOUT, TransactionEvent.EXHAUSTED, TransactionState.FAILED),
                Arguments.of(TransactionState.AUTH_FAILED, TransactionEvent.RETRY_ROUTE, TransactionState.ROUTE_SELECTED),
                Arguments.of(TransactionState.AUTH_FAILED, TransactionEvent.EXHAUSTED, TransactionState.FAILED),
                Arguments.of(TransactionState.AUTHORISED, TransactionEvent.CAPTURE_INITIATED, TransactionState.CAPTURE_INITIATED),
                Arguments.of(TransactionState.AUTHORISED, TransactionEvent.VOID_INITIATED, TransactionState.VOID_INITIATED),
                Arguments.of(TransactionState.AUTHORISED, TransactionEvent.HOLD_EXPIRED, TransactionState.AUTH_EXPIRED),
                Arguments.of(TransactionState.VOID_INITIATED, TransactionEvent.GATEWAY_VOID_SUCCESS, TransactionState.VOIDED),
                Arguments.of(TransactionState.CAPTURE_INITIATED, TransactionEvent.GATEWAY_CAPTURE_SUCCESS, TransactionState.CAPTURED),
                Arguments.of(TransactionState.CAPTURE_INITIATED, TransactionEvent.GATEWAY_PARTIAL_CAPTURE, TransactionState.PARTIALLY_CAPTURED),
                Arguments.of(TransactionState.CAPTURE_INITIATED, TransactionEvent.GATEWAY_CAPTURE_ERROR, TransactionState.CAPTURE_FAILED),
                Arguments.of(TransactionState.CAPTURE_FAILED, TransactionEvent.RETRY_CAPTURE, TransactionState.CAPTURE_INITIATED),
                Arguments.of(TransactionState.CAPTURE_FAILED, TransactionEvent.VOID_INITIATED, TransactionState.VOID_INITIATED),
                Arguments.of(TransactionState.PARTIALLY_CAPTURED, TransactionEvent.CAPTURE_INITIATED, TransactionState.CAPTURE_INITIATED),
                Arguments.of(TransactionState.PARTIALLY_CAPTURED, TransactionEvent.REFUND_INITIATED, TransactionState.REFUND_INITIATED),
                Arguments.of(TransactionState.PARTIALLY_CAPTURED, TransactionEvent.VOID_INITIATED, TransactionState.VOID_INITIATED),
                Arguments.of(TransactionState.PARTIALLY_CAPTURED, TransactionEvent.GATEWAY_SETTLED, TransactionState.SETTLED),
                Arguments.of(TransactionState.CAPTURED, TransactionEvent.REFUND_INITIATED, TransactionState.REFUND_INITIATED),
                Arguments.of(TransactionState.CAPTURED, TransactionEvent.GATEWAY_SETTLED, TransactionState.SETTLED),
                Arguments.of(TransactionState.CAPTURED, TransactionEvent.DISPUTE_OPENED, TransactionState.DISPUTE_OPENED),
                Arguments.of(TransactionState.CAPTURED, TransactionEvent.RECONCILIATION_OVERRIDE, TransactionState.RECONCILIATION_MISMATCH),
                Arguments.of(TransactionState.SETTLED, TransactionEvent.REFUND_INITIATED, TransactionState.REFUND_INITIATED),
                Arguments.of(TransactionState.SETTLED, TransactionEvent.DISPUTE_OPENED, TransactionState.DISPUTE_OPENED),
                Arguments.of(TransactionState.SETTLED, TransactionEvent.RECONCILIATION_OVERRIDE, TransactionState.RECONCILIATION_MISMATCH),
                Arguments.of(TransactionState.REFUND_INITIATED, TransactionEvent.GATEWAY_REFUND_SUCCESS, TransactionState.REFUNDED),
                Arguments.of(TransactionState.REFUND_INITIATED, TransactionEvent.GATEWAY_PARTIAL_REFUND, TransactionState.PARTIALLY_REFUNDED),
                Arguments.of(TransactionState.REFUND_INITIATED, TransactionEvent.GATEWAY_REFUND_ERROR, TransactionState.REFUND_FAILED),
                Arguments.of(TransactionState.REFUND_FAILED, TransactionEvent.RETRY_REFUND, TransactionState.REFUND_INITIATED),
                Arguments.of(TransactionState.PARTIALLY_REFUNDED, TransactionEvent.REFUND_INITIATED, TransactionState.REFUND_INITIATED),
                Arguments.of(TransactionState.DISPUTE_OPENED, TransactionEvent.DISPUTE_RESOLVED, TransactionState.DISPUTE_RESOLVED),
                Arguments.of(TransactionState.AUTH_INITIATED, TransactionEvent.MANDATE_EXPIRED, TransactionState.AUTH_EXPIRED),
                Arguments.of(TransactionState.PARTIALLY_CAPTURED, TransactionEvent.RECONCILIATION_OVERRIDE, TransactionState.RECONCILIATION_MISMATCH)
        );
    }

    // ---------------------------------------------------------------
    // Invalid transitions: satisfies "minimum 15 invalid test cases".
    // Includes FS-15's exact scenario (CREATED cannot become REFUNDED).
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "{0} + {1} should be rejected")
    @MethodSource("invalidTransitions")
    void invalidTransitionsThrow(TransactionState from, TransactionEvent event) {
        assertThatThrownBy(() -> stateMachine.transition(from, event))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(stateMachine.canTransition(from, event)).isFalse();
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(TransactionState.CREATED, TransactionEvent.GATEWAY_REFUND_SUCCESS),
                Arguments.of(TransactionState.CREATED, TransactionEvent.GATEWAY_AUTH_SUCCESS),
                Arguments.of(TransactionState.CREATED, TransactionEvent.CAPTURE_INITIATED),
                Arguments.of(TransactionState.CREATED, TransactionEvent.DISPUTE_OPENED),
                Arguments.of(TransactionState.ABANDONED, TransactionEvent.ROUTE_SELECTED),
                Arguments.of(TransactionState.VOIDED, TransactionEvent.CAPTURE_INITIATED),
                Arguments.of(TransactionState.AUTH_EXPIRED, TransactionEvent.ROUTE_SELECTED),
                Arguments.of(TransactionState.REFUNDED, TransactionEvent.REFUND_INITIATED),
                Arguments.of(TransactionState.DISPUTE_RESOLVED, TransactionEvent.DISPUTE_OPENED),
                Arguments.of(TransactionState.FAILED, TransactionEvent.ROUTE_SELECTED),
                Arguments.of(TransactionState.AUTHORISED, TransactionEvent.GATEWAY_REFUND_SUCCESS),
                Arguments.of(TransactionState.CAPTURED, TransactionEvent.GATEWAY_AUTH_SUCCESS),
                Arguments.of(TransactionState.RECONCILIATION_MISMATCH, TransactionEvent.ADMIN_OVERRIDE),
                Arguments.of(TransactionState.PARTIALLY_CAPTURED, TransactionEvent.GATEWAY_REFUND_SUCCESS),
                Arguments.of(TransactionState.ROUTE_SELECTED, TransactionEvent.GATEWAY_CAPTURE_SUCCESS),
                Arguments.of(TransactionState.SETTLED, TransactionEvent.GATEWAY_AUTH_DECLINE),
                Arguments.of(TransactionState.REFUND_FAILED, TransactionEvent.GATEWAY_REFUND_SUCCESS)
        );
    }

    // ---------------------------------------------------------------
    // Targeted tests for specific spec requirements
    // ---------------------------------------------------------------

    @Test
    void exceptionMessageListsValidEventsFromCurrentState_asRequiredByFS15() {
        assertThatThrownBy(() -> stateMachine.transition(TransactionState.CREATED, TransactionEvent.GATEWAY_REFUND_SUCCESS))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("ROUTE_SELECTED")
                .hasMessageContaining("ABANDONED");
    }

    @Test
    void exceptionCarriesStructuredDataNotJustAMessage() {
        try {
            stateMachine.transition(TransactionState.CREATED, TransactionEvent.GATEWAY_REFUND_SUCCESS);
        } catch (InvalidStateTransitionException ex) {
            assertThat(ex.getFromState()).isEqualTo(TransactionState.CREATED);
            assertThat(ex.getAttemptedEvent()).isEqualTo(TransactionEvent.GATEWAY_REFUND_SUCCESS);
            assertThat(ex.getValidEvents()).containsExactlyInAnyOrder(
                    TransactionEvent.ROUTE_SELECTED, TransactionEvent.ABANDONED);
        }
    }

    @Test
    void adminOverride_onlyWorksFromReconciliationMismatch() {
        TransactionState result = stateMachine.adminOverride(TransactionState.RECONCILIATION_MISMATCH, TransactionState.CAPTURED);
        assertThat(result).isEqualTo(TransactionState.CAPTURED);
    }

    @Test
    void adminOverride_rejectsAnyOtherCurrentState() {
        assertThatThrownBy(() -> stateMachine.adminOverride(TransactionState.CAPTURED, TransactionState.REFUNDED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void terminalStatesHaveNoValidOutgoingEvents() {
        for (TransactionState terminal : new TransactionState[]{
                TransactionState.ABANDONED, TransactionState.VOIDED, TransactionState.AUTH_EXPIRED,
                TransactionState.REFUNDED, TransactionState.DISPUTE_RESOLVED, TransactionState.FAILED}) {
            assertThat(stateMachine.validEventsFrom(terminal))
                    .as("terminal state %s should have no outgoing events", terminal)
                    .isEmpty();
        }
    }
}