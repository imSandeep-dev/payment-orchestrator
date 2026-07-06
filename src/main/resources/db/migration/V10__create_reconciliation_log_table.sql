-- run_id groups every discrepancy found by a single reconciliation batch
-- (A5.5). requires_manual_review defaults TRUE for settlement mismatches
-- per FS-11: "Automatic refund is NOT triggered."

CREATE TABLE reconciliation_log (
    id                       UUID         PRIMARY KEY,
    run_id                   UUID         NOT NULL,
    transaction_id           UUID         NOT NULL REFERENCES transactions(id),
    discrepancy_type         VARCHAR(50)  NOT NULL
                             CHECK (discrepancy_type IN ('STALE_TRANSACTION','SETTLEMENT_MISMATCH','MISSING_SETTLEMENT')),
    internal_state           VARCHAR(30)  NOT NULL,
    gateway_reported_state   VARCHAR(30),
    resolution               VARCHAR(30)  NOT NULL DEFAULT 'PENDING_REVIEW'
                             CHECK (resolution IN ('AUTO_RESOLVED','PENDING_REVIEW','MANUALLY_RESOLVED')),
    requires_manual_review   BOOLEAN      NOT NULL DEFAULT FALSE,
    details                  JSONB,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_run_id  ON reconciliation_log (run_id);
CREATE INDEX idx_reconciliation_txn     ON reconciliation_log (transaction_id);
CREATE INDEX idx_reconciliation_type    ON reconciliation_log (discrepancy_type);