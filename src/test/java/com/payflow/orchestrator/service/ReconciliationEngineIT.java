package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.*;
import com.payflow.orchestrator.gateway.MockInstruction;
import com.payflow.orchestrator.gateway.MockResponseType;
import com.payflow.orchestrator.repository.ReconciliationLogRepository;
import com.payflow.orchestrator.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @SpringBootTest, NOT @DataJpaTest: ReconciliationEngine is a plain
 * @Service, same reasoning as WebhookDedupServiceIT (Day 10) — a JPA test
 * slice doesn't component-scan regular services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ReconciliationEngineIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private ReconciliationEngine engine;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ReconciliationLogRepository reconciliationLogRepository;

    private static final Map<String, MockInstruction> ALL_SUCCESS = Map.of(
            "razorpay", MockInstruction.success(), "stripe", MockInstruction.success(),
            "payu", MockInstruction.success(), "upi", MockInstruction.success());

    /** Persists a transaction and back-dates updatedAt past the 5-minute staleness threshold. */
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Persists a transaction and back-dates updated_at past the 5-minute
     * staleness threshold via direct JDBC, NOT another JPA save() — Transaction
     * has a @PreUpdate hook (touchUpdatedAt()) that would otherwise reset
     * updated_at back to "now" on any further JPA-mediated write, silently
     * cundoing the backdating this test needs.
     */
    private Transaction persistStale(TransactionState state, String gateway, String gatewayReference) {
        Transaction txn = Transaction.create(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                100000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID());
        txn.applyState(state);
        txn.assignGateway(gateway, gatewayReference);
        Transaction saved = transactionRepository.saveAndFlush(txn);

        jdbcTemplate.update("UPDATE transactions SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES)), saved.getId());

        return saved;
    }

    private Transaction persistCaptured(TransactionState state, String gateway, String gatewayReference) {
        Transaction txn = Transaction.create(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                100000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID());
        txn.applyState(state);
        txn.assignGateway(gateway, gatewayReference);
        return transactionRepository.saveAndFlush(txn);
    }

    @Test
    void staleAuthInitiatedConfirmedSuccessResolvesToAuthorised() {
        Transaction txn = persistStale(TransactionState.AUTH_INITIATED, "razorpay", "pay_stale_1");
        UUID runId = UUID.randomUUID();

        engine.runStaleTransactionCheck(runId, ALL_SUCCESS);

        Transaction reloaded = transactionRepository.findById(txn.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TransactionState.AUTHORISED);

        List<ReconciliationLogEntry> log = reconciliationLogRepository.findByRunIdOrderByCreatedAtAsc(runId);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).getResolution()).isEqualTo("AUTO_RESOLVED");
        assertThat(log.get(0).isRequiresManualReview()).isFalse();
    }

    @Test
    void staleAuthInitiatedConfirmedFailureResolvesToAuthFailed() {
        Transaction txn = persistStale(TransactionState.AUTH_INITIATED, "razorpay", "pay_stale_2");
        UUID runId = UUID.randomUUID();
        Map<String, MockInstruction> allFail = Map.of(
                "razorpay", new MockInstruction(MockResponseType.DECLINE, 0, false),
                "stripe", MockInstruction.success(), "payu", MockInstruction.success(), "upi", MockInstruction.success());

        engine.runStaleTransactionCheck(runId, allFail);

        Transaction reloaded = transactionRepository.findById(txn.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TransactionState.AUTH_FAILED);
    }

    @Test
    void settlementMatchProducesNoDiscrepancyLog() {
        persistCaptured(TransactionState.CAPTURED, "razorpay", "pay_match_1");
        UUID runId = UUID.randomUUID();

        engine.runSettlementCheck(runId, ALL_SUCCESS);

        assertThat(engine.getReport(runId)).isEmpty();
    }

    @Test
    void settlementMismatchOnCapturedRoutesToReconciliationMismatch_perFS11() {
        Transaction txn = persistCaptured(TransactionState.CAPTURED, "razorpay", "pay_mismatch_1");
        UUID runId = UUID.randomUUID();
        Map<String, MockInstruction> mismatch = Map.of(
                "razorpay", new MockInstruction(MockResponseType.DECLINE, 0, false),
                "stripe", MockInstruction.success(), "payu", MockInstruction.success(), "upi", MockInstruction.success());

        engine.runSettlementCheck(runId, mismatch);

        Transaction reloaded = transactionRepository.findById(txn.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TransactionState.RECONCILIATION_MISMATCH);

        List<ReconciliationLogEntry> log = reconciliationLogRepository.findByRunIdOrderByCreatedAtAsc(runId);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).getResolution()).isEqualTo("PENDING_REVIEW");
        assertThat(log.get(0).isRequiresManualReview()).isTrue(); // FS-11: never auto-resolved
    }

    @Test
    void settlementMismatchOnPartiallyCapturedAlsoRoutesToReconciliationMismatch_perDay12Fix() {
        // Proves today's state-machine fix: PARTIALLY_CAPTURED -> RECONCILIATION_MISMATCH
        // did not exist before Day 12 (see docs/state-machine.md Amendments).
        Transaction txn = persistCaptured(TransactionState.PARTIALLY_CAPTURED, "razorpay", "pay_mismatch_2");
        UUID runId = UUID.randomUUID();
        Map<String, MockInstruction> mismatch = Map.of(
                "razorpay", new MockInstruction(MockResponseType.DECLINE, 0, false),
                "stripe", MockInstruction.success(), "payu", MockInstruction.success(), "upi", MockInstruction.success());

        engine.runSettlementCheck(runId, mismatch);

        Transaction reloaded = transactionRepository.findById(txn.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TransactionState.RECONCILIATION_MISMATCH);
    }

    @Test
    void getReportOnlyReturnsEntriesForTheRequestedRunId() {
        persistCaptured(TransactionState.CAPTURED, "razorpay", "pay_run_a");
        persistCaptured(TransactionState.CAPTURED, "razorpay", "pay_run_b");
        Map<String, MockInstruction> mismatch = Map.of(
                "razorpay", new MockInstruction(MockResponseType.DECLINE, 0, false),
                "stripe", MockInstruction.success(), "payu", MockInstruction.success(), "upi", MockInstruction.success());

        UUID runA = UUID.randomUUID();
        engine.runSettlementCheck(runA, mismatch);
        UUID runB = UUID.randomUUID();
        engine.runSettlementCheck(runB, mismatch);

        List<ReconciliationLogEntry> reportA = engine.getReport(runA);
        assertThat(reportA).allMatch(e -> e.getRunId().equals(runA));
    }
}