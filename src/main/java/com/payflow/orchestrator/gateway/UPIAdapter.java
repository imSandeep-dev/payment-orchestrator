package com.payflow.orchestrator.gateway;

import org.springframework.stereotype.Component;

/**
 * Section A1.3: UPI has "N/A (instant)" auth+capture, 60s auth timeout, no
 * fixed rate limit, partial refund NOT supported, T+0/T+1 settlement.
 * Matches gateway_config seed data (Day 2, V11): supports_auth_capture =
 * FALSE, supports_partial_refund = FALSE.
 */
@Component
public class UPIAdapter extends AbstractMockPaymentGateway {

    // Section A1.3's "Timeout (Auth)" column — NOT the same timer as FS-12's
    // separate 5-minute customer-approval mandate window (see FS-12 for that).
    private static final int GATEWAY_TIMEOUT_SECONDS = 60;

    @Override
    public String getGatewayName() {
        return "upi";
    }

    @Override
    public GatewayResult authorize(AuthorizationRequest request) {
        return simulate(request.mockInstruction(), "authorize");
    }

    @Override
    public GatewayResult capture(CaptureRequest request) {
        // Same reasoning as PayUAdapter: UPI settles instantly.
        return new GatewayResult(GatewayOutcome.SUCCESS, request.gatewayReference(),
                "{\"status\":\"success\",\"operation\":\"capture\",\"gateway_reference\":\"%s\",\"note\":\"UPI settles instantly\"}"
                        .formatted(request.gatewayReference()),
                null, null);
    }

    @Override
    public GatewayResult voidAuthorization(VoidRequest request) {
        // UPI has no auth-hold to void — there is no separate hold phase.
        // Returning NOT_SUPPORTED (rather than a fake success) avoids
        // misleading a caller into thinking a hold was actually released.
        return new GatewayResult(GatewayOutcome.NOT_SUPPORTED, null,
                "{\"status\":\"error\",\"operation\":\"void\",\"error_code\":\"NOT_APPLICABLE_FOR_UPI\"}",
                "UPI has no auth-hold to void — payment settles instantly", null);
    }

    @Override
    public GatewayResult refund(RefundRequest request) {
        // NOTE: gateway_config.supports_partial_refund = FALSE for UPI, but
        // this adapter does NOT enforce that here — it only simulates what
        // the gateway does given a request. Whether a request IS partial is
        // business context (comparing against the transaction's captured
        // amount) belonging to the refund service arriving Day 11-12, which
        // must check gateway_config before ever calling this with a partial amount.
        return simulate(request.mockInstruction(), "refund");
    }

    @Override
    protected GatewayResult buildTimeoutResult(String operation) {
        // FS-12: the customer didn't approve within the 5-minute mandate
        // window. This is a customer-driven expiry, NOT a network/gateway
        // failure — it must not be treated as failover-eligible the way
        // GatewayOutcome.TIMEOUT is for the other three gateways.
        return new GatewayResult(GatewayOutcome.MANDATE_EXPIRED, null,
                "{\"status\":\"error\",\"operation\":\"%s\",\"error_code\":\"MANDATE_EXPIRED\"}".formatted(operation),
                "UPI collect mandate window (5 minutes) expired without customer response (FS-12)", null);
    }

    @Override
    protected int getAuthTimeoutSeconds() {
        return GATEWAY_TIMEOUT_SECONDS;
    }
}