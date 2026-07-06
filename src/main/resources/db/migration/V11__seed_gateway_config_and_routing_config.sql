-- Static config rows only. Historical performance data (A3.4) is NOT seeded
-- here — that's a Day 7-8 task, once GatewayHealthMetrics exists to consume it.

INSERT INTO gateway_config
    (gateway_name, display_name, supports_auth_capture, supports_partial_refund,
     currency_support, auth_timeout_seconds, rate_limit_per_second,
     cost_percentage, cost_fixed_paise, settlement_days_min, settlement_days_max)
VALUES
    ('razorpay', 'Razorpay', TRUE,  TRUE,  'INR_ONLY',      30, 200,  0.02000, 200, 2, 2),
    ('stripe',   'Stripe',   TRUE,  TRUE,  'MULTI_CURRENCY',30, 100,  0.02500, 300, 2, 2),
    ('payu',     'PayU',     FALSE, TRUE,  'INR_ONLY',      45, 150,  0.01800, 150, 3, 3),
    ('upi',      'UPI (NPCI)', FALSE, FALSE, 'INR_ONLY',    60, NULL, 0.00000, 0,   0, 1);

INSERT INTO routing_config (config_key)
VALUES ('default');   -- all weight_* columns use their DEFAULT values (35/20/20/15/10)