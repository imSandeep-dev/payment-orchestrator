package com.payflow.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.payflow.orchestrator.domain.*;
import com.payflow.orchestrator.exception.ApiException;
import com.payflow.orchestrator.gateway.MockInstruction;
import com.payflow.orchestrator.gateway.MockResponseType;
import com.payflow.orchestrator.repository.GatewayConfigRepository;
import com.payflow.orchestrator.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FailureScenarioEndToEndIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private TransactionService transactionService;
    @Autowired private CaptureRetryExecutor captureRetryExecutor;
    @Autowired private GatewayRouter gatewayRouter;
    @Autowired private GatewayHealthMetrics healthMetrics;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private GatewayConfigRepository gatewayConfigRepository;

    private Map<String, MockInstruction> uniform(MockInstruction instruction) {
        return Map.of("razorpay", instruction, "stripe", instruction, "payu", instruction, "upi", instruction);
    }

    @Test
    void fs01_gatewayTimeoutDuringAuthorisation_failsOverWithinTwoSeconds() {
        Map<String, MockInstruction> instructions = Map.of(
                "razorpay", new MockInstruction(MockResponseType.TIMEOUT, 0, false),
                "stripe", MockInstruction.success(), "payu", MockInstruction.success(), "upi", MockInstruction.success());

        long start = System.currentTimeMillis();
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                100000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString(), instructions);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(txn.getState()).isEqualTo(TransactionState.AUTHORISED);
        assertThat(txn.getGateway()).isNotEqualTo("razorpay");
        assertThat(elapsed).isLessThan(2000); // Section B3's failover benchmark
    }

    @Test
    void fs05_partialCaptureLeavesRemainingHoldCaptureable() throws JsonProcessingException {
        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                120000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString(), uniform(MockInstruction.success()));

        Transaction partiallyCaptured = transactionService.capture(txn.getId(), 80000L, MockInstruction.success());
        assertThat(partiallyCaptured.getState()).isEqualTo(TransactionState.PARTIALLY_CAPTURED);
        assertThat(partiallyCaptured.getCapturedAmountPaise()).isEqualTo(80000L);

        Transaction fullyCaptured = transactionService.capture(txn.getId(), 40000L, MockInstruction.success());
        assertThat(fullyCaptured.getState()).isEqualTo(TransactionState.CAPTURED);
        assertThat(fullyCaptured.getCapturedAmountPaise()).isEqualTo(120000L);
    }

    @Test
    void fs07_cascadeFailure_excludesDownGateway_deprioritizesDegradedGateway() {
        // Razorpay: trip circuit OPEN via 5 consecutive failures.
        for (int i = 0; i < 5; i++) {
            healthMetrics.recordOutcome("razorpay", "CREDIT_CARD", false, 100);
        }
        // PayU: degrade its success rate (still CLOSED, just unhealthy).
        // Keep failures below threshold (5) so circuit stays CLOSED.
        for (int i = 0; i < 3; i++) {
            healthMetrics.recordOutcome("payu", "CREDIT_CARD", false, 900);
        }
        healthMetrics.recordOutcome("payu", "CREDIT_CARD", true, 900);
        healthMetrics.recordOutcome("payu", "CREDIT_CARD", true, 900);
        // Stripe: healthy.
        healthMetrics.recordOutcome("stripe", "CREDIT_CARD", true, 300);

        List<GatewayScore> scores = gatewayRouter.scoreEligibleGateways("CREDIT_CARD", 100000L);

        assertThat(scores).extracting(GatewayScore::gateway).doesNotContain("razorpay"); // OPEN -> excluded
        assertThat(scores.get(0).gateway()).isEqualTo("stripe"); // healthiest wins
        // PayU still eligible (CLOSED) but scores below Stripe due to its degraded success rate.
        assertThat(scores.stream().filter(s -> s.gateway().equals("payu")).findFirst().orElseThrow().totalScore())
                .isLessThan(scores.get(0).totalScore());
    }

    @Test
    void fs08_refundOnAlreadySettledTransactionSucceeds() throws JsonProcessingException {
        Transaction txn = Transaction.create(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                90000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID());
        txn.assignGateway("razorpay", "pay_settled_1");
        txn.applyState(TransactionState.SETTLED);
        txn.recordCapture(90000L);
        transactionRepository.saveAndFlush(txn);

        Refund refund = transactionService.refund(txn.getId(), 90000L, "post-settlement refund", MockInstruction.success());

        assertThat(refund.getState()).isEqualTo("REFUNDED");
        Transaction reloaded = transactionRepository.findById(txn.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TransactionState.REFUNDED);
    }

    @Test
    void fs12_upiCollectFlowTimeout_endsInAuthExpired_noRetry() {
        Map<String, MockInstruction> instructions = uniform(new MockInstruction(MockResponseType.TIMEOUT, 0, false));

        Transaction txn = transactionService.initiatePayment(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                50000L, "INR", PaymentMethod.UPI, UUID.randomUUID().toString(), instructions);

        assertThat(txn.getState()).isEqualTo(TransactionState.AUTH_EXPIRED);
        assertThat(txn.getState().isTerminal()).isTrue();

        List<TransactionStateLog> timeline = transactionService.getTimeline(txn.getId());
        long authAttempts = timeline.stream().filter(l -> l.getEvent().startsWith("AUTHORIZE_ATTEMPT")).count();
        assertThat(authAttempts).isEqualTo(1); // no retry attempted, per FS-12
    }

    @Test
    void fs14_concurrentInitiatePayments_completeWithoutConnectionPoolExhaustionErrors() throws Exception {
        // SCOPED sanity check, not a full load benchmark (that's Day 14,
        // Section B3). Confirms moderate concurrency queues gracefully on
        // HikariCP's default pool rather than throwing unhandled exceptions.
        int concurrentRequests = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        List<Future<Transaction>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            String orderId = "ORD-" + UUID.randomUUID();
            futures.add(executor.submit(() -> transactionService.initiatePayment(
                    UUID.randomUUID(), orderId, 25000L, "INR", PaymentMethod.CREDIT_CARD,
                    UUID.randomUUID().toString(), uniform(MockInstruction.success()))));
        }

        int succeeded = 0;
        for (Future<Transaction> f : futures) {
            Transaction txn = f.get(30, TimeUnit.SECONDS);
            if (txn.getState() == TransactionState.AUTHORISED) succeeded++;
        }
        executor.shutdown();

        assertThat(succeeded).isEqualTo(concurrentRequests);
    }
}