-- Dead-letter-queue pattern per A8.3. Table #11 — justified as a required
-- resilience component, not an arbitrary addition (see design note #6).

CREATE TABLE webhook_queue (
    id             BIGSERIAL    PRIMARY KEY,
    gateway        VARCHAR(50)  NOT NULL,
    event_id       VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    signature      TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','DLQ')),
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    max_retries    INTEGER      NOT NULL DEFAULT 3,
    next_retry_at  TIMESTAMPTZ,
    error_message  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ
);

CREATE INDEX idx_webhook_queue_pending ON webhook_queue (next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');