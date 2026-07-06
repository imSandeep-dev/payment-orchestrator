-- Immutable audit trail, per A2.3. In a real deployment, UPDATE/DELETE
-- privileges on this table should be revoked from the application's DB role
-- entirely (INSERT-only) — noted here, enforced via DB grants in a later
-- hardening pass rather than today's single dev role.

CREATE TABLE transaction_state_log (
    id                UUID         PRIMARY KEY,
    transaction_id    UUID         NOT NULL REFERENCES transactions(id),
    from_state        VARCHAR(30),                 -- NULL only for the very first CREATED entry
    to_state          VARCHAR(30)  NOT NULL,
    event             VARCHAR(100) NOT NULL,
    gateway_reference VARCHAR(255),
    gateway_response  JSONB,                        -- PII-redacted before insert, per A2.3 example
    metadata          JSONB,
    trace_id          UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100) NOT NULL
);

CREATE INDEX idx_state_log_transaction_id ON transaction_state_log (transaction_id);
CREATE INDEX idx_state_log_created_at     ON transaction_state_log (created_at);
CREATE INDEX idx_state_log_transition     ON transaction_state_log (from_state, to_state);