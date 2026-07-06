-- Composite PK (merchant_id, idempotency_key) deviates from the single-column
-- example in A4.2 — required by FS-13 (multi-tenant key collision scenario).

CREATE TABLE idempotency_keys (
    merchant_id      UUID         NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING'
                      CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    response_code    INTEGER,
    response_body    JSONB,
    transaction_id   UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW() + INTERVAL '24 hours',
    PRIMARY KEY (merchant_id, idempotency_key)
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at)
    WHERE status != 'COMPLETED';