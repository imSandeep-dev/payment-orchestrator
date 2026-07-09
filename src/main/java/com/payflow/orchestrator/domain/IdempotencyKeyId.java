package com.payflow.orchestrator.domain;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite ID for idempotency_keys: (merchant_id, idempotency_key).
 * Deliberately scoped to merchant ("Two different
 * merchants accidentally generate the same idempotency key UUID... must be
 * treated as different requests"). This was designed into the schema
 * but only actually exercised in code starting today.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class IdempotencyKeyId implements Serializable {

    private UUID merchantId;
    private String idempotencyKey;

    public IdempotencyKeyId(UUID merchantId, String idempotencyKey) {
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
    }
}