package com.payflow.orchestrator.service;

import java.util.UUID;

public record IdempotencyOutcome(Status status, Integer cachedResponseCode, String cachedResponseBody,
                                 UUID cachedTransactionId) {

    public enum Status { PROCEED, CONFLICT_IN_FLIGHT, CACHED_RESPONSE, KEY_REUSED_DIFFERENT_PAYLOAD }

    public static IdempotencyOutcome proceed() { return new IdempotencyOutcome(Status.PROCEED, null, null, null); }
    public static IdempotencyOutcome conflict() { return new IdempotencyOutcome(Status.CONFLICT_IN_FLIGHT, null, null, null); }
    public static IdempotencyOutcome cached(Integer code, String body, UUID transactionId) {
        return new IdempotencyOutcome(Status.CACHED_RESPONSE, code, body, transactionId);
    }
    public static IdempotencyOutcome keyReusedWithDifferentPayload() {
        return new IdempotencyOutcome(Status.KEY_REUSED_DIFFERENT_PAYLOAD, null, null, null);
    }
}