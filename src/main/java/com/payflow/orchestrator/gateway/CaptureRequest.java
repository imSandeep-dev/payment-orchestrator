package com.payflow.orchestrator.gateway;

import java.util.UUID;

/** amountPaise may be less than the original authorized amount — supports partial capture (FS-05). */
public record CaptureRequest(
        UUID transactionId,
        String gatewayReference,
        long amountPaise,
        UUID traceId,
        MockInstruction mockInstruction
) {}