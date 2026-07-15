package com.payflow.orchestrator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationLogEntry {

    @Id private UUID id;
    @Column(name = "run_id", nullable = false) private UUID runId;
    @Column(name = "transaction_id", nullable = false) private UUID transactionId;
    @Column(name = "discrepancy_type", nullable = false, length = 50) private String discrepancyType;
    @Column(name = "internal_state", nullable = false, length = 30) private String internalState;
    @Column(name = "gateway_reported_state", length = 30) private String gatewayReportedState;
    @Column(name = "resolution", nullable = false, length = 30) private String resolution;
    @Column(name = "requires_manual_review", nullable = false) private boolean requiresManualReview;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String details;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static ReconciliationLogEntry create(UUID runId, UUID transactionId, String discrepancyType,
                                                String internalState, String gatewayReportedState, String resolution,
                                                boolean requiresManualReview, String details) {
        ReconciliationLogEntry e = new ReconciliationLogEntry();
        e.id = UUID.randomUUID();
        e.runId = runId;
        e.transactionId = transactionId;
        e.discrepancyType = discrepancyType;
        e.internalState = internalState;
        e.gatewayReportedState = gatewayReportedState;
        e.resolution = resolution;
        e.requiresManualReview = requiresManualReview;
        e.details = details;
        e.createdAt = Instant.now();
        return e;
    }
}