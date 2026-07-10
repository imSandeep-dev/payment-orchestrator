package com.payflow.orchestrator.webhook;

public record WebhookProcessingResult(Outcome outcome, String message) {

    public enum Outcome {
        PROCESSED, DUPLICATE_ACKNOWLEDGED, SIGNATURE_INVALID,
        TRANSACTION_NOT_FOUND, AMOUNT_MISMATCH, CURRENCY_MISMATCH, INVALID_STATE_TRANSITION
    }

    public boolean isHttp200() {
        return outcome == Outcome.PROCESSED || outcome == Outcome.DUPLICATE_ACKNOWLEDGED;
    }
}