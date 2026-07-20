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
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA requires a no-arg constructor;
// PROTECTED discourages `new Transaction()`
// from business code — use create() instead.
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "merchant_order_id", nullable = false, updatable = false)
    private String merchantOrderId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private TransactionState state;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "captured_amount_paise", nullable = false)
    private long capturedAmountPaise;

    @Column(name = "refunded_amount_paise", nullable = false)
    private long refundedAmountPaise;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, updatable = false,length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "gateway", length = 50)
    private String gateway;

    @Column(name = "gateway_reference")
    private String gatewayReference;

    @Column(name = "trace_id", nullable = false, updatable = false)
    private UUID traceId;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Transaction create(UUID merchantId, String merchantOrderId, long amountPaise,
                                     String currency, PaymentMethod paymentMethod, UUID traceId) {
        Transaction txn = new Transaction();
        txn.id = UUID.randomUUID();               // app-generated, per ADR from Day 2
        txn.merchantId = merchantId;
        txn.merchantOrderId = merchantOrderId;
        txn.state = TransactionState.CREATED;
        txn.amountPaise = amountPaise;
        txn.capturedAmountPaise = 0L;
        txn.refundedAmountPaise = 0L;
        txn.currency = currency;
        txn.paymentMethod = paymentMethod;
        txn.traceId = traceId;
        Instant now = Instant.now();
        txn.createdAt = now;
        txn.updatedAt = now;
        return txn;
    }

    /**
     * Applies a state that has ALREADY been validated by
     * TransactionStateMachine.transition(...) — see Day 3. This entity
     * deliberately does NOT expose a generic setState(TransactionState).
     * Section A2.1 warns that a naive mutable status column lets a bug
     * "skip the CAPTURED state entirely." Routing every state change
     * through TransactionService (arriving Day 7+), which always calls the
     * state machine first, is how we prevent that class of bug structurally
     * rather than by convention/code-review alone.
     */
    public void applyState(TransactionState newState) {
        this.state = newState;
    }

    public void recordCapture(long capturedPaise) {
        this.capturedAmountPaise += capturedPaise;
    }

    public void recordRefund(long refundedPaise) {
        this.refundedAmountPaise += refundedPaise;
    }

    public void assignGateway(String gateway, String gatewayReference) {
        this.gateway = gateway;
        this.gatewayReference = gatewayReference;
    }

    public void assignIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}