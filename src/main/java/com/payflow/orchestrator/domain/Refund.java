package com.payflow.orchestrator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "gateway_refund_id")
    private String gatewayRefundId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "state", nullable = false, length = 30)
    private String state; // REFUND_INITIATED | REFUNDED | PARTIALLY_REFUNDED | REFUND_FAILED

    @Column(name = "reason")
    private String reason;

    @Column(name = "initiated_by", nullable = false, updatable = false, length = 100)
    private String initiatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Refund create(UUID transactionId, long amountPaise, String reason, String initiatedBy) {
        Refund r = new Refund();
        r.id = UUID.randomUUID();
        r.transactionId = transactionId;
        r.amountPaise = amountPaise;
        r.state = "REFUND_INITIATED";
        r.reason = reason;
        r.initiatedBy = initiatedBy;
        Instant now = Instant.now();
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public void markResult(String finalState, String gatewayRefundId) {
        this.state = finalState;
        this.gatewayRefundId = gatewayRefundId;
    }

    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}