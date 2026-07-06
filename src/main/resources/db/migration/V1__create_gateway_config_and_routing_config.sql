-- Static/near-static lookup tables. ~10 rows and ~5 rows respectively (A6.1).
-- These exist independently of any transaction and are read by the
-- GatewayRouter service starting Day 7-8.

CREATE TABLE gateway_config (
    gateway_name                        VARCHAR(50)  PRIMARY KEY,
    display_name                        VARCHAR(100) NOT NULL,
    is_enabled                          BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_auth_capture               BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_partial_refund             BOOLEAN      NOT NULL DEFAULT TRUE,
    currency_support                    VARCHAR(20)  NOT NULL DEFAULT 'INR_ONLY'
                                         CHECK (currency_support IN ('INR_ONLY', 'MULTI_CURRENCY')),
    auth_timeout_seconds                INTEGER      NOT NULL,
    rate_limit_per_second               INTEGER,                         -- NULL = no fixed limit (e.g. UPI, per A1.3)
    cost_percentage                     NUMERIC(6,5) NOT NULL DEFAULT 0,  -- e.g. 0.02000 = 2%
    cost_fixed_paise                    BIGINT       NOT NULL DEFAULT 0, -- e.g. 200 = ₹2.00
    settlement_days_min                 INTEGER      NOT NULL,
    settlement_days_max                 INTEGER      NOT NULL,
    circuit_breaker_failure_threshold   INTEGER      NOT NULL DEFAULT 5,
    circuit_breaker_timeout_seconds     INTEGER      NOT NULL DEFAULT 30,
    circuit_breaker_half_open_max_calls INTEGER      NOT NULL DEFAULT 1,
    webhook_secret_ref                  VARCHAR(255),  -- reference/alias only; the real secret
                                                        -- lives in env vars / a secrets manager,
                                                        -- never in this table.
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE routing_config (
    config_key                          VARCHAR(100) PRIMARY KEY,
    weight_success_rate                 NUMERIC(4,3) NOT NULL DEFAULT 0.350,
    weight_latency                      NUMERIC(4,3) NOT NULL DEFAULT 0.200,
    weight_cost                         NUMERIC(4,3) NOT NULL DEFAULT 0.200,
    weight_health                       NUMERIC(4,3) NOT NULL DEFAULT 0.150,
    weight_payment_method_fit           NUMERIC(4,3) NOT NULL DEFAULT 0.100,
    degraded_score_gap_threshold        NUMERIC(4,3) NOT NULL DEFAULT 0.200, -- the "20%" rule, A3.2
    sliding_window_minutes              INTEGER      NOT NULL DEFAULT 5,
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT weights_sum_to_one CHECK (
        ABS((weight_success_rate + weight_latency + weight_cost
             + weight_health + weight_payment_method_fit) - 1.000) < 0.001
    )
);