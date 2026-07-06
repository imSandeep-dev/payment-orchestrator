-- Records every routing decision and its scoring breakdown — critical for
-- debugging "why did the router pick Stripe over Razorpay at 18:03?"

CREATE TABLE gateway_routes (
    id                      UUID         PRIMARY KEY,
    transaction_id          UUID         NOT NULL REFERENCES transactions(id),
    gateway                 VARCHAR(50)  NOT NULL REFERENCES gateway_config(gateway_name),
    score                   NUMERIC(7,5) NOT NULL,
    success_rate_component  NUMERIC(7,5),
    latency_component       NUMERIC(7,5),
    cost_component          NUMERIC(7,5),
    health_component        NUMERIC(7,5),
    fit_component           NUMERIC(7,5),
    was_selected            BOOLEAN      NOT NULL DEFAULT FALSE,
    circuit_breaker_state   VARCHAR(20),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gateway_routes_transaction ON gateway_routes (transaction_id);
CREATE INDEX idx_gateway_routes_gateway     ON gateway_routes (gateway);
CREATE INDEX idx_gateway_routes_score       ON gateway_routes (score);