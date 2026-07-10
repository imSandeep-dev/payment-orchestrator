package com.payflow.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.payflow.orchestrator.domain.PaymentMethod;
import com.payflow.orchestrator.domain.Refund;
import com.payflow.orchestrator.domain.Transaction;
import com.payflow.orchestrator.domain.TransactionState;
import com.payflow.orchestrator.exception.ApiException;
import com.payflow.orchestrator.gateway.MockInstruction;
import com.payflow.orchestrator.gateway.MockResponseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TransactionServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private TransactionService transactionService;

    private Map<String, MockInstruction> uniform(MockInstruction instruction) {
        return Map.of("razorpay", instruction, "stripe", instruction, "payu", instruction, "upi", instruction);
    }

    @Test
    void initiatePaymentSucceedsEndToEnd() {
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                100000L, "INR", PaymentMethod.UPI, UUID.randomUUID().toString(), uniform(MockInstruction.success()));

        assertThat(txn.getState()).isEqualTo(TransactionState.AUTHORISED);
        assertThat(txn.getGateway()).isEqualTo("upi");
    }

    @Test
    void sameIdempotencyKeyReturnsCachedTransaction() {
        String key = UUID.randomUUID().toString();
        UUID merchantId = UUID.randomUUID();
        String orderId = "ORD-" + UUID.randomUUID();

        Transaction first = transactionService.initiatePayment(merchantId, orderId, 50000L, "INR",
                PaymentMethod.UPI, key, uniform(MockInstruction.success()));
        Transaction second = transactionService.initiatePayment(merchantId, orderId, 50000L, "INR",
                PaymentMethod.UPI, key, uniform(MockInstruction.success()));

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void captureExceedingAuthorizedAmountIsRejected() {
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                50000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString(), uniform(MockInstruction.success()));

        assertThatThrownBy(() -> transactionService.capture(txn.getId(), 999999L, MockInstruction.success()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exceeds available");
    }

    @Test
    void captureSucceedsAndUpdatesCapturedAmount() throws JsonProcessingException {
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                80000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString(), uniform(MockInstruction.success()));

        Transaction captured = transactionService.capture(txn.getId(), 80000L, MockInstruction.success());

        assertThat(captured.getState()).isEqualTo(TransactionState.CAPTURED);
        assertThat(captured.getCapturedAmountPaise()).isEqualTo(80000L);
    }

    @Test
    void refundSucceedsAndCreatesRefundRecord() throws JsonProcessingException {
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                60000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString(), uniform(MockInstruction.success()));
        transactionService.capture(txn.getId(), 60000L, MockInstruction.success());

        Refund refund = transactionService.refund(txn.getId(), 60000L, "customer requested", MockInstruction.success());

        assertThat(refund.getState()).isEqualTo("REFUNDED");
        List<Refund> refunds = transactionService.getRefunds(txn.getId());
        assertThat(refunds).hasSize(1);
    }

    @Test
    void partialRefundOnUpiIsRejected_perSectionA13() throws JsonProcessingException {
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                40000L, "INR", PaymentMethod.UPI, UUID.randomUUID().toString(), uniform(MockInstruction.success()));
        transactionService.capture(txn.getId(), 40000L, MockInstruction.success());

        assertThatThrownBy(() -> transactionService.refund(txn.getId(), 10000L, "partial", MockInstruction.success()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not support partial refunds");
    }

    @Test
    void timelineReflectsFullLifecycle() throws JsonProcessingException {
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                30000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString(), uniform(MockInstruction.success()));
        transactionService.capture(txn.getId(), 30000L, MockInstruction.success());

        List<?> timeline = transactionService.getTimeline(txn.getId());
        assertThat(timeline.size()).isGreaterThanOrEqualTo(4); // CREATED, ROUTE_SELECTED, auth attempt, capture x2
    }

    @Test
    void voidFailureLeavesTransactionInVoidInitiated_perKnownGapADR007() {
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                20000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString(), uniform(MockInstruction.success()));

        assertThatThrownBy(() -> transactionService.voidAuthorization(txn.getId(),
                new MockInstruction(MockResponseType.SERVER_ERROR, 0, false)))
                .isInstanceOf(ApiException.class);

        Transaction reloaded = transactionService.getById(txn.getId());
        assertThat(reloaded.getState()).isEqualTo(TransactionState.VOID_INITIATED);
    }
}