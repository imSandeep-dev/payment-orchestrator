package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.Refund;

import java.time.Instant;
import java.util.UUID;

public record RefundResponse(UUID id, UUID transactionId, String gatewayRefundId, long amountPaise,
                             String state, String reason, Instant createdAt) {
    public static RefundResponse from(Refund r) {
        return new RefundResponse(r.getId(), r.getTransactionId(), r.getGatewayRefundId(),
                r.getAmountPaise(), r.getState(), r.getReason(), r.getCreatedAt());
    }
}