package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.TransactionStateLog;

import java.time.Instant;

public record TimelineEntryResponse(String fromState, String toState, String event,
                                    String gatewayReference, Instant createdAt) {
    public static TimelineEntryResponse from(TransactionStateLog log) {
        return new TimelineEntryResponse(
                log.getFromState() == null ? null : log.getFromState().name(),
                log.getToState().name(), log.getEvent(), log.getGatewayReference(), log.getCreatedAt());
    }
}