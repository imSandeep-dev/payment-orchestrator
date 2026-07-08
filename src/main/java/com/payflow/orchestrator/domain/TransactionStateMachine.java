package com.payflow.orchestrator.domain;

import com.payflow.orchestrator.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.payflow.orchestrator.domain.TransactionState.AUTH_INITIATED;

@Component
public class TransactionStateMachine {

    private static final Map<TransactionState, Map<TransactionEvent, TransactionState>> TRANSITIONS =
            buildTransitionTable();

    public TransactionState transition(TransactionState currentState, TransactionEvent event) {
        Map<TransactionEvent, TransactionState> eventsForState =
                TRANSITIONS.getOrDefault(currentState, Map.of());
        TransactionState nextState = eventsForState.get(event);
        if (nextState == null) {
            throw new InvalidStateTransitionException(currentState, event, eventsForState.keySet());
        }
        return nextState;
    }

    public boolean canTransition(TransactionState currentState, TransactionEvent event) {
        return TRANSITIONS.getOrDefault(currentState, Map.of()).containsKey(event);
    }

    public Set<TransactionEvent> validEventsFrom(TransactionState state) {
        return TRANSITIONS.getOrDefault(state, Map.of()).keySet();
    }

    public TransactionState adminOverride(TransactionState currentState, TransactionState targetState) {
        if (currentState != TransactionState.RECONCILIATION_MISMATCH) {
            throw new InvalidStateTransitionException(currentState, TransactionEvent.ADMIN_OVERRIDE, Set.of());
        }
        Objects.requireNonNull(targetState, "targetState must not be null for an admin override");
        return targetState;
    }

    private static Map<TransactionState, Map<TransactionEvent, TransactionState>> buildTransitionTable() {
        Map<TransactionState, Map<TransactionEvent, TransactionState>> table = new EnumMap<>(TransactionState.class);

        addTransition(table, TransactionState.CREATED, TransactionEvent.ROUTE_SELECTED, TransactionState.ROUTE_SELECTED);
        addTransition(table, TransactionState.CREATED, TransactionEvent.ABANDONED, TransactionState.ABANDONED);

        addTransition(table, TransactionState.ROUTE_SELECTED, TransactionEvent.AUTH_INITIATED, AUTH_INITIATED);
        addTransition(table, TransactionState.ROUTE_SELECTED, TransactionEvent.ROUTE_FAILED, TransactionState.ROUTE_FAILED);

        addTransition(table, TransactionState.ROUTE_FAILED, TransactionEvent.RETRY_ROUTE, TransactionState.ROUTE_SELECTED);
        addTransition(table, TransactionState.ROUTE_FAILED, TransactionEvent.EXHAUSTED, TransactionState.FAILED);

        addTransition(table, AUTH_INITIATED, TransactionEvent.GATEWAY_AUTH_SUCCESS, TransactionState.AUTHORISED);
        addTransition(table, AUTH_INITIATED, TransactionEvent.GATEWAY_AUTH_DECLINE, TransactionState.AUTH_FAILED);
        addTransition(table, AUTH_INITIATED, TransactionEvent.GATEWAY_TIMEOUT, TransactionState.AUTH_TIMEOUT);
        addTransition(table, AUTH_INITIATED, TransactionEvent.MANDATE_EXPIRED, TransactionState.AUTH_EXPIRED);

        addTransition(table, TransactionState.AUTH_TIMEOUT, TransactionEvent.FAILOVER, TransactionState.ROUTE_SELECTED);
        addTransition(table, TransactionState.AUTH_TIMEOUT, TransactionEvent.EXHAUSTED, TransactionState.FAILED);

        addTransition(table, TransactionState.AUTH_FAILED, TransactionEvent.RETRY_ROUTE, TransactionState.ROUTE_SELECTED);
        addTransition(table, TransactionState.AUTH_FAILED, TransactionEvent.EXHAUSTED, TransactionState.FAILED);

        addTransition(table, TransactionState.AUTHORISED, TransactionEvent.CAPTURE_INITIATED, TransactionState.CAPTURE_INITIATED);
        addTransition(table, TransactionState.AUTHORISED, TransactionEvent.VOID_INITIATED, TransactionState.VOID_INITIATED);
        addTransition(table, TransactionState.AUTHORISED, TransactionEvent.HOLD_EXPIRED, TransactionState.AUTH_EXPIRED);

        addTransition(table, TransactionState.VOID_INITIATED, TransactionEvent.GATEWAY_VOID_SUCCESS, TransactionState.VOIDED);

        addTransition(table, TransactionState.CAPTURE_INITIATED, TransactionEvent.GATEWAY_CAPTURE_SUCCESS, TransactionState.CAPTURED);
        addTransition(table, TransactionState.CAPTURE_INITIATED, TransactionEvent.GATEWAY_PARTIAL_CAPTURE, TransactionState.PARTIALLY_CAPTURED);
        addTransition(table, TransactionState.CAPTURE_INITIATED, TransactionEvent.GATEWAY_CAPTURE_ERROR, TransactionState.CAPTURE_FAILED);

        addTransition(table, TransactionState.CAPTURE_FAILED, TransactionEvent.RETRY_CAPTURE, TransactionState.CAPTURE_INITIATED);
        addTransition(table, TransactionState.CAPTURE_FAILED, TransactionEvent.VOID_INITIATED, TransactionState.VOID_INITIATED);

        addTransition(table, TransactionState.PARTIALLY_CAPTURED, TransactionEvent.CAPTURE_INITIATED, TransactionState.CAPTURE_INITIATED);
        addTransition(table, TransactionState.PARTIALLY_CAPTURED, TransactionEvent.REFUND_INITIATED, TransactionState.REFUND_INITIATED);
        addTransition(table, TransactionState.PARTIALLY_CAPTURED, TransactionEvent.VOID_INITIATED, TransactionState.VOID_INITIATED);
        addTransition(table, TransactionState.PARTIALLY_CAPTURED, TransactionEvent.GATEWAY_SETTLED, TransactionState.SETTLED);

        addTransition(table, TransactionState.CAPTURED, TransactionEvent.REFUND_INITIATED, TransactionState.REFUND_INITIATED);
        addTransition(table, TransactionState.CAPTURED, TransactionEvent.GATEWAY_SETTLED, TransactionState.SETTLED);
        addTransition(table, TransactionState.CAPTURED, TransactionEvent.DISPUTE_OPENED, TransactionState.DISPUTE_OPENED);
        addTransition(table, TransactionState.CAPTURED, TransactionEvent.RECONCILIATION_OVERRIDE, TransactionState.RECONCILIATION_MISMATCH);

        addTransition(table, TransactionState.SETTLED, TransactionEvent.REFUND_INITIATED, TransactionState.REFUND_INITIATED);
        addTransition(table, TransactionState.SETTLED, TransactionEvent.DISPUTE_OPENED, TransactionState.DISPUTE_OPENED);
        addTransition(table, TransactionState.SETTLED, TransactionEvent.RECONCILIATION_OVERRIDE, TransactionState.RECONCILIATION_MISMATCH);

        addTransition(table, TransactionState.REFUND_INITIATED, TransactionEvent.GATEWAY_REFUND_SUCCESS, TransactionState.REFUNDED);
        addTransition(table, TransactionState.REFUND_INITIATED, TransactionEvent.GATEWAY_PARTIAL_REFUND, TransactionState.PARTIALLY_REFUNDED);
        addTransition(table, TransactionState.REFUND_INITIATED, TransactionEvent.GATEWAY_REFUND_ERROR, TransactionState.REFUND_FAILED);

        addTransition(table, TransactionState.REFUND_FAILED, TransactionEvent.RETRY_REFUND, TransactionState.REFUND_INITIATED);

        addTransition(table, TransactionState.PARTIALLY_REFUNDED, TransactionEvent.REFUND_INITIATED, TransactionState.REFUND_INITIATED);

        addTransition(table, TransactionState.DISPUTE_OPENED, TransactionEvent.DISPUTE_RESOLVED, TransactionState.DISPUTE_RESOLVED);

        return Collections.unmodifiableMap(table);
    }

    private static void addTransition(Map<TransactionState, Map<TransactionEvent, TransactionState>> table,
                                      TransactionState from, TransactionEvent event, TransactionState to) {
        table.computeIfAbsent(from, k -> new EnumMap<>(TransactionEvent.class)).put(event, to);
    }
}