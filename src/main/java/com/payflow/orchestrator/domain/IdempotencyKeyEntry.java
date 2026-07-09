package com.payflow.orchestrator.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Maps idempotency_keys. Same no-blanket-setter discipline as every entity. */
@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyEntry {

    @EmbeddedId
    private IdempotencyKeyId id;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PROCESSING | COMPLETED | FAILED — matches the DB CHECK constraint

    @Column(name = "response_code")
    private Integer responseCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static IdempotencyKeyEntry startProcessing(UUID merchantId, String idempotencyKey, String requestHash) {
        IdempotencyKeyEntry e = new IdempotencyKeyEntry();
        e.id = new IdempotencyKeyId(merchantId, idempotencyKey);
        e.requestHash = requestHash;
        e.status = "PROCESSING";
        Instant now = Instant.now();
        e.createdAt = now;
        e.updatedAt = now;
        e.expiresAt = now.plus(24, ChronoUnit.HOURS);
        return e;
    }

    public boolean isProcessing() {
        return "PROCESSING".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public void markCompleted(int responseCode, String responseBodyJson, UUID transactionId) {
        this.status = "COMPLETED";
        this.responseCode = responseCode;
        this.responseBody = responseBodyJson;
        this.transactionId = transactionId;
    }

    public void markFailed() {
        this.status = "FAILED";
    }

    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}