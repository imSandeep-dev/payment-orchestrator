package com.payflow.orchestrator.gateway;

import org.springframework.stereotype.Component;

/**
 * Section A1.3: PayU has "Limited" auth+capture support, 45 s auth timeout,
 * 150 req/sec rate limit, partial refund supported, T+3 settlement.
 * Matches gateway_config.supports_auth_capture = FALSE (Day 2, V11 seed data).
 */
@Component
public class PayUAdapter extends AbstractMockPaymentGateway {

    private static final int AUTH_TIMEOUT_SECONDS = 45;

    @Override
    public String getGatewayName() {
        return "payu";
    }

    @Override
    public GatewayResult authorize(AuthorizationRequest request) {
        return simulate(request.mockInstruction(), "authorize");
    }

    @Override
    public GatewayResult capture(CaptureRequest request) {
        // "Limited" auth+capture support means funds effectively settle at
        // authorization time, not via a genuinely separate capture step.
        // capture() is therefore an idempotent confirmation, NOT a re-run of
        // simulate() — a real PayU capture can't fail after the fact, since
        // the money already moved during authorize().
        return new GatewayResult(GatewayOutcome.SUCCESS, request.gatewayReference(),
                "{\"status\":\"success\",\"operation\":\"capture\",\"gateway_reference\":\"%s\",\"note\":\"PayU auto-captures at authorization\"}"
                        .formatted(request.gatewayReference()),
                null, null);
    }

    @Override
    public GatewayResult voidAuthorization(VoidRequest request) {
        return simulate(request.mockInstruction(), "void");
    }

    @Override
    public GatewayResult refund(RefundRequest request) {
        // PayU DOES support partial refunds (Section A1.3) — no special-casing needed.
        return simulate(request.mockInstruction(), "refund");
    }

    @Override
    protected int getAuthTimeoutSeconds() {
        return AUTH_TIMEOUT_SECONDS;
    }
}