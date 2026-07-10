package com.payflow.orchestrator.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.payflow.orchestrator.domain.*;
import com.payflow.orchestrator.repository.TransactionRepository;
import com.payflow.orchestrator.repository.TransactionStateLogRepository;
import com.payflow.orchestrator.util.HmacUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class WebhookEventProcessorIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private WebhookEventProcessor processor;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionStateLogRepository stateLogRepository;

    // Must match application.yml's app.webhook-secrets.gateways.razorpay
    private static final String RAZORPAY_SECRET = "dev-razorpay-webhook-secret-change-me";

    private Transaction persistTransaction(TransactionState initialState, long amountPaise, String gatewayReference) {
        Transaction txn = Transaction.create(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                amountPaise, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID());
        txn.assignGateway("razorpay", gatewayReference);
        txn.applyState(initialState);
        return transactionRepository.saveAndFlush(txn);
    }

    private String sign(String body, String secret) {
        return HmacUtil.hmacHex("HmacSHA256", secret, body);
    }

    @Test
    void validCapturedWebhookAppliesFullChainFromAuthInitiated_perFS06() throws JsonProcessingException {
        Transaction txn = persistTransaction(TransactionState.AUTH_INITIATED, 100000L, "pay_test_1");
        String body = "{\"status\":\"captured\"}";
        var request = new IncomingWebhookRequest("razorpay", "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_CAPTURED, "pay_test_1", 100000L, "INR", body, sign(body, RAZORPAY_SECRET));

        WebhookProcessingResult result = processor.process(request);

        assertThat(result.outcome()).isEqualTo(WebhookProcessingResult.Outcome.PROCESSED);
        Transaction reloaded = transactionRepository.findById(txn.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TransactionState.CAPTURED);
        assertThat(reloaded.getCapturedAmountPaise()).isEqualTo(100000L);

        // A2.3: one audit log entry per REAL intermediate transition, not one fake skip-edge.
        List<TransactionStateLog> history = stateLogRepository.findByTransactionIdOrderByCreatedAtAsc(txn.getId());
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getToState()).isEqualTo(TransactionState.AUTHORISED);
        assertThat(history.get(1).getToState()).isEqualTo(TransactionState.CAPTURE_INITIATED);
        assertThat(history.get(2).getToState()).isEqualTo(TransactionState.CAPTURED);
    }

    @Test
    void duplicateWebhookDeliveryIsAcknowledgedWithoutReprocessing_perFS02() throws JsonProcessingException {
        persistTransaction(TransactionState.AUTHORISED, 50000L, "pay_test_2");
        String body = "{\"status\":\"captured\"}";
        var request = new IncomingWebhookRequest("razorpay", "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_CAPTURED, "pay_test_2", 50000L, "INR", body, sign(body, RAZORPAY_SECRET));

        WebhookProcessingResult first = processor.process(request);
        WebhookProcessingResult second = processor.process(request);
        WebhookProcessingResult third = processor.process(request);

        assertThat(first.outcome()).isEqualTo(WebhookProcessingResult.Outcome.PROCESSED);
        assertThat(second.outcome()).isEqualTo(WebhookProcessingResult.Outcome.DUPLICATE_ACKNOWLEDGED);
        assertThat(third.outcome()).isEqualTo(WebhookProcessingResult.Outcome.DUPLICATE_ACKNOWLEDGED);
        assertThat(second.isHttp200()).isTrue();
    }

    @Test
    void invalidSignatureIsRejected_perFS10() throws JsonProcessingException {
        persistTransaction(TransactionState.AUTHORISED, 10000L, "pay_test_3");
        String body = "{\"status\":\"captured\"}";
        var request = new IncomingWebhookRequest("razorpay", "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_CAPTURED, "pay_test_3", 10000L, "INR", body, "totally-fake-signature");

        WebhookProcessingResult result = processor.process(request);

        assertThat(result.outcome()).isEqualTo(WebhookProcessingResult.Outcome.SIGNATURE_INVALID);
        assertThat(result.isHttp200()).isFalse();
    }

    @Test
    void tamperedAmountIsRejectedDespiteValidSignatureOverTheTamperedBody_perCaseStudyC4() throws JsonProcessingException {
        persistTransaction(TransactionState.AUTHORISED, 10000L, "pay_test_4"); // real order: ₹100.00

        String tamperedBody = "{\"status\":\"captured\",\"amount\":10000000}"; // attacker claims ₹1,00,000
        var request = new IncomingWebhookRequest("razorpay", "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_CAPTURED, "pay_test_4", 10000000L, "INR",
                tamperedBody, sign(tamperedBody, RAZORPAY_SECRET));

        WebhookProcessingResult result = processor.process(request);

        assertThat(result.outcome()).isEqualTo(WebhookProcessingResult.Outcome.AMOUNT_MISMATCH);
    }

    @Test
    void unknownGatewayReferenceIsRejected() throws JsonProcessingException {
        String body = "{\"status\":\"captured\"}";
        var request = new IncomingWebhookRequest("razorpay", "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_CAPTURED, "pay_does_not_exist", 10000L, "INR",
                body, sign(body, RAZORPAY_SECRET));

        WebhookProcessingResult result = processor.process(request);

        assertThat(result.outcome()).isEqualTo(WebhookProcessingResult.Outcome.TRANSACTION_NOT_FOUND);
    }

    @Test
    void reversedWebhookOnCapturedTransactionFlagsReconciliationMismatch_perCaseStudyC2() throws JsonProcessingException {
        Transaction txn = persistTransaction(TransactionState.CAPTURED, 75000L, "pay_test_6");
        String body = "{\"status\":\"reversed\"}";
        var request = new IncomingWebhookRequest("razorpay", "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_REVERSED, "pay_test_6", 75000L, "INR", body, sign(body, RAZORPAY_SECRET));

        WebhookProcessingResult result = processor.process(request);

        assertThat(result.outcome()).isEqualTo(WebhookProcessingResult.Outcome.PROCESSED);
        Transaction reloaded = transactionRepository.findById(txn.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TransactionState.RECONCILIATION_MISMATCH);
    }

    @Test
    void refundWebhookOnUncapturedTransactionIsRejectedGracefully() throws JsonProcessingException {
        persistTransaction(TransactionState.AUTH_INITIATED, 20000L, "pay_test_7");
        String body = "{\"status\":\"refunded\"}";
        var request = new IncomingWebhookRequest("razorpay", "evt_" + UUID.randomUUID(),
                WebhookEventType.PAYMENT_REFUNDED, "pay_test_7", 20000L, "INR", body, sign(body, RAZORPAY_SECRET));

        WebhookProcessingResult result = processor.process(request);

        assertThat(result.outcome()).isEqualTo(WebhookProcessingResult.Outcome.INVALID_STATE_TRANSITION);
    }
}