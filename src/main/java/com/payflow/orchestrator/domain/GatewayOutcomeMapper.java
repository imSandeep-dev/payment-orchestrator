package com.payflow.orchestrator.domain;

import com.payflow.orchestrator.gateway.GatewayOutcome;

/**
 * Translates a gateway's raw outcome into the TransactionEvent
 * vocabulary the state machine understands.
 *
 * Scoped to the AUTHORIZE operation only for now — capture/refund/void
 * outcome mapping arrives as TransactionService builds out those
 * flows, since each operation maps its outcomes onto a different set of
 * events (e.g., SUCCESS means GATEWAY_AUTH_SUCCESS during authorize, but
 * GATEWAY_CAPTURE_SUCCESS during capture).
 */
public final class GatewayOutcomeMapper {

    private GatewayOutcomeMapper() {
    }

    /**
     * SERVER_ERROR and RATE_LIMITED are deliberately folded onto the same
     * event as TIMEOUT: failure-point table treats a gateway
     * 5xx and a true timeout with the identical recovery action (retry/
     * failover). The state machine doesn't need to distinguish them — the
     * precise distinction is preserved in the audit trail's raw
     * gateway_response JSON, not in the state itself.
     */
    public static TransactionEvent forAuthorize(GatewayOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> TransactionEvent.GATEWAY_AUTH_SUCCESS;
            case DECLINED -> TransactionEvent.GATEWAY_AUTH_DECLINE;
            case TIMEOUT, SERVER_ERROR, RATE_LIMITED -> TransactionEvent.GATEWAY_TIMEOUT;
            case MANDATE_EXPIRED -> TransactionEvent.MANDATE_EXPIRED;
            case NOT_SUPPORTED -> throw new IllegalArgumentException(
                    "NOT_SUPPORTED is not a valid authorize() outcome for any gateway");
        };
    }

    /**
     * Distinguishes full vs. partial capture by comparing what's being
     * captured now against what was still available to capture.
     */
    public static TransactionEvent forCapture(GatewayOutcome outcome, long captureAmountPaise, long remainingBeforeCapture) {
        if (outcome == GatewayOutcome.SUCCESS) {
            return captureAmountPaise == remainingBeforeCapture
                    ? TransactionEvent.GATEWAY_CAPTURE_SUCCESS
                    : TransactionEvent.GATEWAY_PARTIAL_CAPTURE;
        }
        return TransactionEvent.GATEWAY_CAPTURE_ERROR;
    }

    /**
     * The 24-state design has no VOID_FAILED state —
     * VOID_INITIATED's only outgoing event is GATEWAY_VOID_SUCCESS. A failed
     * void therefore has no valid TransactionEvent to map to; this throws
     * rather than silently mapping to something incorrect. Callers MUST check
     * GatewayResult.isSuccess() before calling this.
     */
    public static TransactionEvent forVoid(GatewayOutcome outcome) {
        if (outcome != GatewayOutcome.SUCCESS) {
            throw new IllegalStateException(
                    "Void failure has no modeled state-machine transition (see ADR-007 backlog item). Outcome was: " + outcome);
        }
        return TransactionEvent.GATEWAY_VOID_SUCCESS;
    }

    public static TransactionEvent forRefund(GatewayOutcome outcome, long refundAmountPaise, long remainingBeforeRefund) {
        if (outcome == GatewayOutcome.SUCCESS) {
            return refundAmountPaise == remainingBeforeRefund
                    ? TransactionEvent.GATEWAY_REFUND_SUCCESS
                    : TransactionEvent.GATEWAY_PARTIAL_REFUND;
        }
        return TransactionEvent.GATEWAY_REFUND_ERROR;
    }
}