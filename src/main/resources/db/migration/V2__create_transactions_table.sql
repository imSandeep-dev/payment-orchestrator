-- The core transaction record. State transitions themselves are enforced in
-- application code (TransactionStateMachine, Day 3-4); this CHECK constraint
-- is a second line of defense against corrupted/garbage data only.

CREATE TABLE transactions (
    id                     UUID         PRIMARY KEY,           -- app-generated, see design note #5
    merchant_id            UUID         NOT NULL,
    merchant_order_id      VARCHAR(255) NOT NULL,
    idempotency_key        VARCHAR(255),
    state                  VARCHAR(30)  NOT NULL,
    amount_paise           BIGINT       NOT NULL CHECK (amount_paise > 0),
    captured_amount_paise  BIGINT       NOT NULL DEFAULT 0 CHECK (captured_amount_paise >= 0),
    refunded_amount_paise  BIGINT       NOT NULL DEFAULT 0 CHECK (refunded_amount_paise >= 0),
    currency               CHAR(3)      NOT NULL DEFAULT 'INR',
    payment_method         VARCHAR(20)  NOT NULL
                            CHECK (payment_method IN ('CREDIT_CARD','DEBIT_CARD','UPI','NETBANKING','WALLET')),
    gateway                VARCHAR(50)  REFERENCES gateway_config(gateway_name),
    gateway_reference      VARCHAR(255),
    trace_id               UUID         NOT NULL,
    version                INTEGER      NOT NULL DEFAULT 0,     -- optimistic-lock belt-and-suspenders
                                                                 -- alongside SELECT FOR UPDATE (A8.1)
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_merchant_order UNIQUE (merchant_id, merchant_order_id),

    CONSTRAINT chk_transaction_state CHECK (state IN (
        'CREATED','ROUTE_SELECTED','ROUTE_FAILED','ABANDONED',
        'AUTH_INITIATED','AUTHORISED','AUTH_FAILED','AUTH_TIMEOUT','AUTH_EXPIRED',
        'VOID_INITIATED','VOIDED',
        'CAPTURE_INITIATED','CAPTURED','PARTIALLY_CAPTURED','CAPTURE_FAILED',
        'SETTLED',
        'REFUND_INITIATED','REFUNDED','PARTIALLY_REFUNDED','REFUND_FAILED',
        'DISPUTE_OPENED','DISPUTE_RESOLVED',
        'RECONCILIATION_MISMATCH',
        'FAILED'
    ))
);

CREATE INDEX idx_transactions_gateway_reference ON transactions (gateway_reference);
CREATE INDEX idx_transactions_state             ON transactions (state);
CREATE INDEX idx_transactions_created_at        ON transactions (created_at);