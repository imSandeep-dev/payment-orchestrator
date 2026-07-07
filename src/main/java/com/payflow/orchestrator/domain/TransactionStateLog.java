package com.payflow.orchestrator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable row per state transition (Section A2.3). No setters at
 * all — once created, a log entry is never modified. If you find yourself
 * wanting to update one, that's a signal to append a new entry instead
 * (or route through the reconciliation flow, Day 11-12).
 */
@Entity
@Table(name = "transaction_state_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionStateLog {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", updatable = false, length = 30)
    private TransactionState fromState; // null only for the very first CREATED entry

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, updatable = false, length = 30)
    private TransactionState toState;

    @Column(name = "event", nullable = false, updatable = false, length = 100)
    private String event;

    @Column(name = "gateway_reference", updatable = false)
    private String gatewayReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_response", updatable = false, columnDefinition = "jsonb")
    private String gatewayResponse; // ALREADY sanitized — see GatewayResponseSanitizer

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", updatable = false, columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "trace_id", updatable = false)
    private UUID traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    public static TransactionStateLog record(UUID transactionId, TransactionState fromState,
                                             TransactionState toState, String event,
                                             String gatewayReference,
                                             String sanitizedGatewayResponseJson,
                                             String metadataJson, UUID traceId, String createdBy) {
        TransactionStateLog log = new TransactionStateLog();
        log.id = UUID.randomUUID();
        log.transactionId = transactionId;
        log.fromState = fromState;
        log.toState = toState;
        log.event = event;
        log.gatewayReference = gatewayReference;
        log.gatewayResponse = sanitizedGatewayResponseJson;
        log.metadata = metadataJson;
        log.traceId = traceId;
        log.createdAt = Instant.now();
        log.createdBy = createdBy;
        return log;
    }
}