CREATE TABLE refunds (
    id                 UUID         PRIMARY KEY,
    transaction_id     UUID         NOT NULL REFERENCES transactions(id),
    gateway_refund_id  VARCHAR(255),
    amount_paise       BIGINT       NOT NULL CHECK (amount_paise > 0),
    state              VARCHAR(30)  NOT NULL
                       CHECK (state IN ('REFUND_INITIATED','REFUNDED','PARTIALLY_REFUNDED','REFUND_FAILED')),
    reason             VARCHAR(255),
    initiated_by       VARCHAR(100) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_transaction    ON refunds (transaction_id);
CREATE INDEX idx_refunds_gateway_refund ON refunds (gateway_refund_id);
CREATE INDEX idx_refunds_state          ON refunds (state);