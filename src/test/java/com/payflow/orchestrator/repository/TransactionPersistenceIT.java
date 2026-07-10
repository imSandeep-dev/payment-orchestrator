package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.PaymentMethod;
import com.payflow.orchestrator.domain.Transaction;
import com.payflow.orchestrator.domain.TransactionState;
import com.payflow.orchestrator.domain.TransactionStateLog;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL via Testcontainers — deliberately NOT H2 or another
 * in-memory substitute.money-safety rules and the JSONB
 * audit trail only mean something if tested against the exact database
 * engine we deploy on; an in-memory stand-in could silently accept
 * something real Postgres would reject.
 */
@DataJpaTest
@Testcontainers
class TransactionPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionStateLogRepository stateLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndReloadsTransaction_preservingExactPaiseAmount() {
        Transaction txn = Transaction.create(
                UUID.randomUUID(), "ORD-1001", 120050L, "INR", PaymentMethod.UPI, UUID.randomUUID());

        Transaction saved = transactionRepository.saveAndFlush(txn);
        Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();

        // The core money-safety assertion from Section A6.2.
        assertThat(reloaded.getAmountPaise()).isEqualTo(120050L);
        assertThat(reloaded.getState()).isEqualTo(TransactionState.CREATED);
        assertThat(reloaded.getVersion()).isEqualTo(0);
    }

    @Test
    void amountPaiseColumnIsBigintNotFloatingPoint() {
        // Defensive schema assertion: catches a future migration that
        // accidentally changes amount_paise to NUMERIC or FLOAT.
        List<?> result = entityManager.createNativeQuery(
                        "SELECT data_type FROM information_schema.columns " +
                                "WHERE table_name = 'transactions' AND column_name = 'amount_paise'")
                .getResultList();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).toString()).isEqualToIgnoringCase("bigint");
    }

    @Test
    void optimisticLockingIncrementsVersionOnUpdate() {
        Transaction txn = Transaction.create(
                UUID.randomUUID(), "ORD-1002", 50000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID());
        Transaction saved = transactionRepository.saveAndFlush(txn);
        int versionAfterInsert = saved.getVersion();

        saved.recordCapture(50000L);
        Transaction updated = transactionRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(versionAfterInsert + 1);
    }

    @Test
    void merchantOrderIdIsUniquePerMerchant_asEnforcedByDbConstraint() {
        UUID merchantId = UUID.randomUUID();
        transactionRepository.saveAndFlush(Transaction.create(
                merchantId, "ORD-DUPLICATE", 10000L, "INR", PaymentMethod.UPI, UUID.randomUUID()));

        Transaction duplicate = Transaction.create(
                merchantId, "ORD-DUPLICATE", 20000L, "INR", PaymentMethod.UPI, UUID.randomUUID());

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stateLogEntriesArePersistedAndQueryableInOrder() {
        Transaction saved = transactionRepository.saveAndFlush(Transaction.create(
                UUID.randomUUID(), "ORD-1003", 75000L, "INR", PaymentMethod.NETBANKING, UUID.randomUUID()));

        stateLogRepository.saveAndFlush(TransactionStateLog.record(
                saved.getId(), null, TransactionState.CREATED, "PAYMENT_REQUEST_RECEIVED",
                null, null, null, saved.getTraceId(), "api"));
        stateLogRepository.saveAndFlush(TransactionStateLog.record(
                saved.getId(), TransactionState.CREATED, TransactionState.ROUTE_SELECTED,
                "ROUTE_SELECTED", null, null, null, saved.getTraceId(), "gateway_router"));

        List<TransactionStateLog> history =
                stateLogRepository.findByTransactionIdOrderByCreatedAtAsc(saved.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getToState()).isEqualTo(TransactionState.CREATED);
        assertThat(history.get(1).getToState()).isEqualTo(TransactionState.ROUTE_SELECTED);
    }

    @Test
    void gatewayResponseIsStoredAsRedactedJsonb() {
        Transaction saved = transactionRepository.saveAndFlush(Transaction.create(
                UUID.randomUUID(), "ORD-1004", 30000L, "INR", PaymentMethod.CREDIT_CARD, UUID.randomUUID()));

        String sanitizedJson = "{\"status\":\"captured\",\"card_number\":\"***REDACTED***\"}";

        TransactionStateLog savedEntry = stateLogRepository.saveAndFlush(TransactionStateLog.record(
                saved.getId(), TransactionState.CAPTURE_INITIATED, TransactionState.CAPTURED,
                "GATEWAY_CAPTURE_SUCCESS", "pay_abc123", sanitizedJson, null,
                saved.getTraceId(), "webhook_processor"));

        TransactionStateLog reloaded = stateLogRepository.findById(savedEntry.getId()).orElseThrow();

        assertThat(reloaded.getGatewayResponse()).contains("***REDACTED***");
        assertThat(reloaded.getGatewayResponse()).doesNotContain("4111");
    }
}