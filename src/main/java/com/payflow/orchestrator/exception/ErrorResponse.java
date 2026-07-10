package com.payflow.orchestrator.exception;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, Map<String, Object> details,
                            String requestId, String timestamp) {}

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(new ErrorBody(code, message, details,
                "req_" + UUID.randomUUID(), Instant.now().toString()));
    }
}