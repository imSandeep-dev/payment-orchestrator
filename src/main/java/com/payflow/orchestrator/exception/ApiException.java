package com.payflow.orchestrator.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final Map<String, Object> details;

    public ApiException(HttpStatus httpStatus, String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.details = details;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getDetails() { return details; }

    public static ApiException transactionNotFound(java.util.UUID id) {
        return new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND",
                "No transaction found with id " + id, Map.of("transactionId", id.toString()));
    }

    public static ApiException idempotencyConflict() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_PROCESSING",
                "Another request with this idempotency key is already in progress", Map.of());
    }

    public static ApiException idempotencyKeyReused() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "This idempotency key was already used with a different request payload", Map.of());
    }

    public static ApiException invalidTransactionState(String currentState, String action) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSACTION_STATE",
                "Cannot " + action + " a transaction in state " + currentState,
                Map.of("currentState", currentState, "attemptedAction", action));
    }

    public static ApiException amountExceedsAvailable(long requested, long available, String action) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_EXCEEDS_AVAILABLE",
                "Requested %s amount %d exceeds available amount %d".formatted(action, requested, available),
                Map.of("requestedPaise", requested, "availablePaise", available));
    }

    public static ApiException partialRefundNotSupported(String gateway) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PARTIAL_REFUND_NOT_SUPPORTED",
                "Gateway " + gateway + " does not support partial refunds (Section A1.3)", Map.of("gateway", gateway));
    }

    public static ApiException gatewayOperationFailed(String action, String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "GATEWAY_OPERATION_FAILED",
                "Gateway " + action + " failed: " + detail, Map.of());
    }

    public static ApiException routingWeightsInvalid(java.math.BigDecimal sum) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ROUTING_WEIGHTS_INVALID",
                "Routing weights must sum to 1.0 (got %s) — mirrors the DB-level weights_sum_to_one CHECK constraint (Day 2, V1)".formatted(sum),
                Map.of("sum", sum.toString()));
    }

    public static ApiException gatewayNotFound(String name) {
        return new ApiException(HttpStatus.NOT_FOUND, "GATEWAY_NOT_FOUND", "No gateway named " + name, Map.of("gateway", name));
    }
}