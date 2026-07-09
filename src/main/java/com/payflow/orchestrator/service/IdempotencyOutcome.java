package com.payflow.orchestrator.service;

/** Result of IdempotencyService.checkAndLock() — the caller branches on status(). */
public record IdempotencyOutcome(Status status, Integer cachedResponseCode, String cachedResponseBody) {

    public enum Status {
        PROCEED,                       // no prior attempt (or prior to one was discarded) — go ahead and process
        CONFLICT_IN_FLIGHT,            // another request with this key is currently PROCESSING
        CACHED_RESPONSE,               // a prior COMPLETED attempt exists — return it, don't reprocess
        KEY_REUSED_DIFFERENT_PAYLOAD   // same key, but the request body doesn't match the original
    }

    public static IdempotencyOutcome proceed() {
        return new IdempotencyOutcome(Status.PROCEED, null, null);
    }

    public static IdempotencyOutcome conflict() {
        return new IdempotencyOutcome(Status.CONFLICT_IN_FLIGHT, null, null);
    }

    public static IdempotencyOutcome cached(Integer responseCode, String responseBody) {
        return new IdempotencyOutcome(Status.CACHED_RESPONSE, responseCode, responseBody);
    }

    public static IdempotencyOutcome keyReusedWithDifferentPayload() {
        return new IdempotencyOutcome(Status.KEY_REUSED_DIFFERENT_PAYLOAD, null, null);
    }
}