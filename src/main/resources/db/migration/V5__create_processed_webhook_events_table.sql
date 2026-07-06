-- Composite PK (gateway, event_id) exactly as specified in A5.4 — different
-- gateways may reuse event ID formats, so the gateway must be part of the key.

CREATE TABLE processed_webhook_events (
    event_id       VARCHAR(255) NOT NULL,
    gateway        VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload_hash   VARCHAR(64)  NOT NULL,
    transaction_id UUID,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (gateway, event_id)
);

CREATE INDEX idx_processed_webhook_txn ON processed_webhook_events (transaction_id);