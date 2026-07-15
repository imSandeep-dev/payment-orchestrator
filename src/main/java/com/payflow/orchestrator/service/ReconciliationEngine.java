package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.*;
import com.payflow.orchestrator.gateway.*;
import com.payflow.orchestrator.repository.ReconciliationLogRepository;
import com.payflow.orchestrator.repository.TransactionRepository;
import com.payflow.orchestrator.repository.TransactionStateLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
public class ReconciliationEngine {

    private static final int STALE_THRESHOLD_MINUTES = 5;
    private final TransactionRepository transactionRepository;
    private final TransactionStateLogRepository stateLogRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;
    private final TransactionStateMachine stateMachine;
    private final Map<String, PaymentGateway> gatewaysByName;

    public ReconciliationEngine(TransactionRepository transactionRepository,
                                TransactionStateLogRepository stateLogRepository,
                                ReconciliationLogRepository reconciliationLogRepository,
                                TransactionStateMachine stateMachine, List<PaymentGateway> gateways) {
        this.transactionRepository = transactionRepository;
        this.stateLogRepository = stateLogRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
        this.stateMachine = stateMachine;
        this.gatewaysByName = gateways.stream().collect(Collectors.toMap(PaymentGateway::getGatewayName, g -> g));
    }

    @Transactional
    public void runStaleTransactionCheck(UUID runId, Map<String, MockInstruction> mockInstructionsByGateway) {
        Instant cutoff = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<Transaction> stale = transactionRepository.findByStateInAndUpdatedAtBefore(
                List.of(TransactionState.AUTH_INITIATED, TransactionState.CAPTURE_INITIATED), cutoff);

        for (Transaction txn : stale) {
            reconcileStale(runId, txn, mockInstructionsByGateway);
        }
    }

    private void reconcileStale(UUID runId, Transaction txn, Map<String, MockInstruction> instructions) {
        PaymentGateway gateway = gatewaysByName.get(txn.getGateway());
        if (gateway == null) return; // never actually routed to a gateway — nothing to poll

        boolean isAuth = txn.getState() == TransactionState.AUTH_INITIATED;
        String expected = isAuth ? "AUTHORISED" : "CAPTURED";
        MockInstruction instruction = instructions.getOrDefault(txn.getGateway(), MockInstruction.success());
        GatewayStatusResult status = gateway.checkStatus(new StatusCheckRequest(txn.getGatewayReference(), expected, instruction));

        TransactionState before = txn.getState();
        boolean confirmed = status.reportedState().equals(expected);
        TransactionEvent event = confirmed
                ? (isAuth ? TransactionEvent.GATEWAY_AUTH_SUCCESS : TransactionEvent.GATEWAY_CAPTURE_SUCCESS)
                : (isAuth ? TransactionEvent.GATEWAY_AUTH_DECLINE : TransactionEvent.GATEWAY_CAPTURE_ERROR);

        TransactionState next = stateMachine.transition(before, event);
        txn.applyState(next);
        transactionRepository.save(txn);
        stateLogRepository.save(TransactionStateLog.record(txn.getId(), before, next, event.name(),
                txn.getGatewayReference(), status.rawResponseJson(), null, txn.getTraceId(), "reconciliation_engine"));

        reconciliationLogRepository.save(ReconciliationLogEntry.create(runId, txn.getId(), "STALE_TRANSACTION",
                before.name(), status.reportedState(), "AUTO_RESOLVED", false, status.rawResponseJson()));
    }

    @Transactional
    public void runSettlementCheck(UUID runId, Map<String, MockInstruction> mockInstructionsByGateway) {
        List<Transaction> capturedTxns = transactionRepository.findByStateIn(
                List.of(TransactionState.CAPTURED, TransactionState.PARTIALLY_CAPTURED));

        for (Transaction txn : capturedTxns) {
            PaymentGateway gateway = gatewaysByName.get(txn.getGateway());
            if (gateway == null) continue;

            MockInstruction instruction = mockInstructionsByGateway.getOrDefault(txn.getGateway(), MockInstruction.success());
            GatewayStatusResult status = gateway.checkStatus(
                    new StatusCheckRequest(txn.getGatewayReference(), txn.getState().name(), instruction));

            if (status.reportedState().equals(txn.getState().name())) {
                continue; // gateway confirms what we already have on file — no discrepancy
            }

            //Auto-resolved, NEVER auto-nEVER refunded. Human review only.
            TransactionState before = txn.getState();
            TransactionState next = stateMachine.transition(before, TransactionEvent.RECONCILIATION_OVERRIDE);
            txn.applyState(next);
            transactionRepository.save(txn);
            stateLogRepository.save(TransactionStateLog.record(txn.getId(), before, next, "RECONCILIATION_OVERRIDE",
                    txn.getGatewayReference(), status.rawResponseJson(), null, txn.getTraceId(), "reconciliation_engine"));

            reconciliationLogRepository.save(ReconciliationLogEntry.create(runId, txn.getId(), "SETTLEMENT_MISMATCH",
                    before.name(), status.reportedState(), "PENDING_REVIEW", true, status.rawResponseJson()));
        }
    }

    public List<ReconciliationLogEntry> getReport(UUID runId) {
        return reconciliationLogRepository.findByRunIdOrderByCreatedAtAsc(runId);
    }
}