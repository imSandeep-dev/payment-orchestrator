package com.payflow.orchestrator.gateway;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared simulation engine for all four mock adapters (Section B4.3).
 * Concrete adapters supply only their identity and gateway-specific timeout
 * constant (Section A1.3) — the "how do we simulate each response type"
 * logic lives exactly once, here.
 *
 * See ADR-002 for why TIMEOUT returns promptly rather than literally
 * blocking for the real-world timeout window.
 */
public abstract class AbstractMockPaymentGateway implements PaymentGateway {

    protected GatewayResult simulate(MockInstruction instruction, String operation) {
        applyConfiguredDelay(instruction);

        if (instruction.gatewayDown()) {
            return new GatewayResult(GatewayOutcome.SERVER_ERROR, null,
                    errorEnvelope(operation, "GATEWAY_UNREACHABLE"),
                    "Gateway is completely unreachable (X-Mock-Gateway-Down)", null);
        }

        return switch (instruction.responseType()) {
            case SUCCESS -> {
                String gatewayReference = generateGatewayReference();
                yield new GatewayResult(GatewayOutcome.SUCCESS, gatewayReference,
                        successEnvelope(operation, gatewayReference), null, null);
            }
            case DECLINE -> new GatewayResult(GatewayOutcome.DECLINED, null,
                    errorEnvelope(operation, "INSUFFICIENT_FUNDS"),
                    "Payment declined by issuing bank", null);
            case SERVER_ERROR -> new GatewayResult(GatewayOutcome.SERVER_ERROR, null,
                    errorEnvelope(operation, "INTERNAL_SERVER_ERROR"),
                    "Gateway returned a 5xx error", null);
            case RATE_LIMIT -> new GatewayResult(GatewayOutcome.RATE_LIMITED, null,
                    errorEnvelope(operation, "RATE_LIMIT_EXCEEDED"),
                    "Gateway rate limit exceeded", 2L);
            case TIMEOUT -> new GatewayResult(GatewayOutcome.TIMEOUT, null,
                    errorEnvelope(operation, "TIMEOUT"),
                    "No response within " + getAuthTimeoutSeconds() + "s (simulated, see ADR-002)", null);
        };
    }

    private void applyConfiguredDelay(MockInstruction instruction) {
        if (instruction.delayMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(instruction.delayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating gateway delay", e);
        }
    }

    private String generateGatewayReference() {
        return getGatewayName() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String successEnvelope(String operation, String gatewayReference) {
        return "{\"status\":\"success\",\"operation\":\"%s\",\"gateway_reference\":\"%s\",\"timestamp\":\"%s\"}"
                .formatted(operation, gatewayReference, Instant.now());
    }

    private String errorEnvelope(String operation, String errorCode) {
        return "{\"status\":\"error\",\"operation\":\"%s\",\"error_code\":\"%s\",\"timestamp\":\"%s\"}"
                .formatted(operation, errorCode, Instant.now());
    }

    /** Per-gateway auth timeout in seconds — Section A1.3's comparison table. Informational only (see ADR-002). */
    protected abstract int getAuthTimeoutSeconds();
}