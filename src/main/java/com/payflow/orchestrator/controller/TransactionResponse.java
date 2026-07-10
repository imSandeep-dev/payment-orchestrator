package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.Transaction;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID id, UUID merchantId, String merchantOrderId, String state,
                                  long amountPaise, long capturedAmountPaise, long refundedAmountPaise,
                                  String currency, String paymentMethod, String gateway,
                                  String gatewayReference, Instant createdAt, Instant updatedAt) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getId(), t.getMerchantId(), t.getMerchantOrderId(),
                t.getState().name(), t.getAmountPaise(), t.getCapturedAmountPaise(), t.getRefundedAmountPaise(),
                t.getCurrency(), t.getPaymentMethod().name(), t.getGateway(), t.getGatewayReference(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}