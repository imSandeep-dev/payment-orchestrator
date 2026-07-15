package com.payflow.orchestrator.service;

import com.payflow.orchestrator.gateway.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements FS-04: "Orchestrator retries capture with exponential backoff
 * (1s, 2s, 4s). After 3 failed retries, state moves to CAPTURE_FAILED.
 * System polls gateway status API to determine if capture was actually
 * processed server-side (late success pattern)."
 *
 * UNLIKE ADR-002's TIMEOUT handling, this class genuinely sleeps between
 * retries — FS-04 is specifically about the backoff timing behavior
 * itself, not just the router's reaction to a signal. Only the full-
 * exhaustion path pays the ~7s real cost, and only one test exercises it.
 */
@Component
public class CaptureRetryExecutor {

    private static final long[] BACKOFF_MS = {1000, 2000, 4000};

    private final Map<String, PaymentGateway> gatewaysByName;

    public CaptureRetryExecutor(List<PaymentGateway> gateways) {
        this.gatewaysByName = gateways.stream().collect(Collectors.toMap(PaymentGateway::getGatewayName, g -> g));
    }

    /**
     * @param captureInstruction     mock signal applied to each capture attempt
     * @param statusCheckInstruction mock signal applied to the post-exhaustion
     *                               status poll — deliberately separate from
     *                               captureInstruction so tests can simulate
     *                               the "late success" case (attempts fail,
     *                               but the gateway actually processed it).
     */
    public GatewayResult captureWithRetry(String gatewayName, UUID transactionId, String gatewayReference,
                                          long amountPaise, UUID traceId, MockInstruction captureInstruction,
                                          MockInstruction statusCheckInstruction) {
        PaymentGateway gateway = gatewaysByName.get(gatewayName);
        GatewayResult lastResult = null;

        for (long delayMs : BACKOFF_MS) {
            lastResult = gateway.capture(new CaptureRequest(transactionId, gatewayReference, amountPaise, traceId, captureInstruction));
            if (lastResult.isSuccess()) {
                return lastResult;
            }
            sleep(delayMs);
        }

        GatewayStatusResult status = gateway.checkStatus(
                new StatusCheckRequest(gatewayReference, "CAPTURED", statusCheckInstruction));
        if ("CAPTURED".equals(status.reportedState())) {
            return new GatewayResult(GatewayOutcome.SUCCESS, gatewayReference, status.rawResponseJson(), null, null);
        }
        return lastResult;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during capture retry backoff", e);
        }
    }
}