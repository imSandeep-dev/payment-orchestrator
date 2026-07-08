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
}