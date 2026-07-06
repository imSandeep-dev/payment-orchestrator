package com.payflow.orchestrator.domain;

/**
 * The 24 states of a transaction's lifecycle. See docs/state-machine.md for
 * the full transition table and the FS-number justifying each state beyond
 * the spec's required minimum of 12 (Section A2.2).
 */
public enum TransactionState {
    CREATED(Category.INITIAL),
    ROUTE_SELECTED(Category.INTERMEDIATE),
    ROUTE_FAILED(Category.INTERMEDIATE),
    ABANDONED(Category.TERMINAL),
    AUTH_INITIATED(Category.INTERMEDIATE),
    AUTHORISED(Category.INTERMEDIATE),
    AUTH_FAILED(Category.INTERMEDIATE),
    AUTH_TIMEOUT(Category.INTERMEDIATE),
    AUTH_EXPIRED(Category.TERMINAL),
    VOID_INITIATED(Category.INTERMEDIATE),
    VOIDED(Category.TERMINAL),
    CAPTURE_INITIATED(Category.INTERMEDIATE),
    CAPTURED(Category.INTERMEDIATE),
    PARTIALLY_CAPTURED(Category.INTERMEDIATE),
    CAPTURE_FAILED(Category.INTERMEDIATE),
    SETTLED(Category.INTERMEDIATE),
    REFUND_INITIATED(Category.INTERMEDIATE),
    REFUNDED(Category.TERMINAL),
    PARTIALLY_REFUNDED(Category.INTERMEDIATE),
    REFUND_FAILED(Category.INTERMEDIATE),
    DISPUTE_OPENED(Category.INTERMEDIATE),
    DISPUTE_RESOLVED(Category.TERMINAL),
    RECONCILIATION_MISMATCH(Category.MANUAL_INTERVENTION),
    FAILED(Category.TERMINAL);

    private final Category category;

    TransactionState(Category category) {
        this.category = category;
    }

    public boolean isTerminal() {
        return category == Category.TERMINAL;
    }

    public boolean requiresManualIntervention() {
        return category == Category.MANUAL_INTERVENTION;
    }

    public enum Category {
        INITIAL, INTERMEDIATE, TERMINAL, MANUAL_INTERVENTION
    }
}