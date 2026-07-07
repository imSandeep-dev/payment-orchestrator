package com.payflow.orchestrator.gateway;

import java.util.UUID;

public record AuthorizationRequest(
        UUID transactionId,
        long amountPaise,
        String currency,
        String paymentMethod,
        UUID traceId,
        MockInstruction mockInstruction
) {}