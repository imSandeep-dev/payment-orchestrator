package com.payflow.orchestrator.exception;

import com.payflow.orchestrator.domain.TransactionEvent;
import com.payflow.orchestrator.domain.TransactionState;

import java.util.Set;

/**
 * Thrown when code attempts an illegal state transition — e.g. a buggy
 * refund handler trying to move CREATED straight to REFUNDED (FS-15).
 *
 * Carries structured data (not just a message) so both production error
 * handlers and tests can inspect exactly what was attempted and what
 * would have been valid instead.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final TransactionState fromState;
    private final TransactionEvent attemptedEvent;
    private final Set<TransactionEvent> validEvents;

    public InvalidStateTransitionException(TransactionState fromState,
                                           TransactionEvent attemptedEvent,
                                           Set<TransactionEvent> validEvents) {
        super(buildMessage(fromState, attemptedEvent, validEvents));
        this.fromState = fromState;
        this.attemptedEvent = attemptedEvent;
        this.validEvents = validEvents;
    }

    private static String buildMessage(TransactionState fromState,
                                       TransactionEvent attemptedEvent,
                                       Set<TransactionEvent> validEvents) {
        String validEventsDescription = validEvents.isEmpty()
                ? "none (this is a terminal or manual-intervention-only state)"
                : validEvents.toString();
        return "Cannot apply event '%s' to a transaction in state '%s'. Valid events from '%s' are: %s"
                .formatted(attemptedEvent, fromState, fromState, validEventsDescription);
    }

    public TransactionState getFromState() {
        return fromState;
    }

    public TransactionEvent getAttemptedEvent() {
        return attemptedEvent;
    }

    public Set<TransactionEvent> getValidEvents() {
        return validEvents;
    }
}