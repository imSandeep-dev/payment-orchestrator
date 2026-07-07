package com.payflow.orchestrator.gateway;

import java.util.UUID;

public record VoidRequest(
        UUID transactionId,
        String gatewayReference,
        UUID traceId,
        MockInstruction mockInstruction
) {}