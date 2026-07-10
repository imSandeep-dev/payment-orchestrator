package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.TransactionState;
import com.payflow.orchestrator.gateway.GatewayOutcome;

public record AttemptOutcome(String gateway, GatewayOutcome outcome, TransactionState resultingState,
                             long elapsedMs, String rawResponseJson) {}