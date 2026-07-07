package com.payflow.orchestrator.gateway;

import java.util.UUID;

public record RefundRequest(
        UUID transactionId,
        String gatewayReference,
        long amountPaise,
        UUID traceId,
        MockInstruction mockInstruction
) {}