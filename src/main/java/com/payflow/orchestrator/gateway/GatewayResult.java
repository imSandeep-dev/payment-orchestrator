package com.payflow.orchestrator.gateway;

/**
 * Uniform result type for every gateway operation. rawResponseJson is the
 * UN-sanitized payload — whoever writes this to transaction_state_log MUST
 * pass it through GatewayResponseSanitizer (Day 4) first.
 */
public record GatewayResult(
        GatewayOutcome outcome,
        String gatewayReference,
        String rawResponseJson,
        String errorMessage,
        Long retryAfterSeconds  // populated only for RATE_LIMITED, mirrors a Retry-After header
) {
    public boolean isSuccess() {
        return outcome == GatewayOutcome.SUCCESS;
    }
}