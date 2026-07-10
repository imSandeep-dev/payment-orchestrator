package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.*;
import com.payflow.orchestrator.repository.TransactionRepository;
import com.payflow.orchestrator.repository.TransactionStateLogRepository;
import com.payflow.orchestrator.gateway.GatewayOutcome;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Component
public class VoidLifecycleRecorder {

    private final TransactionRepository transactionRepository;
    private final TransactionStateLogRepository stateLogRepository;
    private final TransactionStateMachine stateMachine;

    public VoidLifecycleRecorder(TransactionRepository transactionRepository,
                                 TransactionStateLogRepository stateLogRepository,
                                 TransactionStateMachine stateMachine) {
        this.transactionRepository = transactionRepository;
        this.stateLogRepository = stateLogRepository;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public TransactionState recordVoidInitiated(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId).orElseThrow();
        TransactionState from = txn.getState();
        TransactionState initiated = stateMachine.transition(from, TransactionEvent.VOID_INITIATED);
        txn.applyState(initiated);
        transactionRepository.save(txn);
        stateLogRepository.save(TransactionStateLog.record(transactionId, from, initiated, "VOID_INITIATED",
                txn.getGatewayReference(), null, null, txn.getTraceId(), "transaction_service"));
        return initiated;
    }

    @Transactional
    public void recordVoidFailure(UUID transactionId, TransactionState currentState, String sanitizedResponse) {
        Transaction txn = transactionRepository.findById(transactionId).orElseThrow();
        stateLogRepository.save(TransactionStateLog.record(transactionId, currentState, currentState,
                "GATEWAY_VOID_ERROR", txn.getGatewayReference(), sanitizedResponse, null,
                txn.getTraceId(), "transaction_service"));
    }

    @Transactional
    public Transaction recordVoidSuccess(UUID transactionId, TransactionState currentState, String sanitizedResponse) {
        Transaction txn = transactionRepository.findById(transactionId).orElseThrow();
        TransactionEvent event = GatewayOutcomeMapper.forVoid(GatewayOutcome.SUCCESS);
        TransactionState next = stateMachine.transition(currentState, event);
        txn.applyState(next);
        transactionRepository.save(txn);
        stateLogRepository.save(TransactionStateLog.record(transactionId, currentState, next, event.name(),
                txn.getGatewayReference(), sanitizedResponse, null, txn.getTraceId(), "transaction_service"));
        return txn;
    }
}