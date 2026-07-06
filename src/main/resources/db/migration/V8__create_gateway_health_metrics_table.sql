-- Circuit breaker is per-gateway AND per-payment-method (A3.3), so both are
-- part of the uniqueness constraint.

CREATE TABLE gateway_health_metrics (
    id                     BIGSERIAL    PRIMARY KEY,
    gateway                VARCHAR(50)  NOT NULL REFERENCES gateway_config(gateway_name),
    payment_method         VARCHAR(20)  NOT NULL,
    window_start           TIMESTAMPTZ  NOT NULL,
    window_end             TIMESTAMPTZ  NOT NULL,
    success_count          INTEGER      NOT NULL DEFAULT 0,
    failure_count          INTEGER      NOT NULL DEFAULT 0,
    total_count            INTEGER      NOT NULL DEFAULT 0,
    p95_latency_ms         INTEGER,
    circuit_breaker_state  VARCHAR(20)  NOT NULL DEFAULT 'CLOSED'
                           CHECK (circuit_breaker_state IN ('CLOSED','OPEN','HALF_OPEN')),
    consecutive_failures   INTEGER      NOT NULL DEFAULT 0,
    circuit_opened_at      TIMESTAMPTZ,
    recorded_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gateway_method_window UNIQUE (gateway, payment_method, window_start)
);

CREATE INDEX idx_health_metrics_gateway      ON gateway_health_metrics (gateway);
CREATE INDEX idx_health_metrics_recorded_at  ON gateway_health_metrics (recorded_at);