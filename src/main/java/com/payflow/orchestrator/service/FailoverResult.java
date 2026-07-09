package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.TransactionState;

import java.util.List;

public record FailoverResult(TransactionState finalState, String successfulGateway,
                             String gatewayReference, List<AttemptOutcome> attempts) {
    public boolean isSuccess() {
        return finalState == TransactionState.AUTHORISED;
    }
}