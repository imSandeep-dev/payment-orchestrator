# Routing Algorithm Specification

Implements Section A3 of the project spec. This document is the
authoritative reference for `GatewayRouter` (Day 7) and `CircuitBreaker`
(Day 8).

## Scoring Formula (A3.2)

Score(gateway) =
(W_success × NormalizedSuccessRate) +
(W_latency × (1 − NormalizedLatency)) +
(W_cost × (1 − NormalizedCost)) +
(W_health × HealthScore) +
(W_fit × FitScore)

Default weights (stored in `routing_config`, changeable via
`PUT /api/v1/routing/config` without redeployment):

| Factor             | Weight |
|--------------------|--------|
| Success Rate       | 35%    |
| Latency            | 20%    |
| Cost               | 20%    |
| Gateway Health     | 15%    |
| Payment Method Fit | 10%    |

## Selection Process

1. Filter to `enabled` gateways supporting the requested payment method
   (UPI is a hard filter — UPI-only gateway, never a soft score factor;
   see ADR-003 #3).
2. Exclude any gateway whose circuit is `OPEN` — **before** computing the
   min-max normalization baseline, so an excluded gateway's extreme
   values can't skew the remaining candidates (ADR-003 #2).
3. Score every remaining candidate.
4. If the top scorer is `HALF_OPEN` (degraded) and its lead over the
   second-best is within the configured gap threshold (default 20%),
   prefer the second-best instead.

## Circuit Breaker (A3.3)

Three states — `CLOSED` → `OPEN` (after N consecutive failures, default
5) → `HALF_OPEN` (after a timeout, default 30s, allows exactly 1 test
   request) → back to `CLOSED` on success or `OPEN` on failure. Implemented
   per-gateway *and* per-payment-method (`CircuitBreaker`, Day 8), backed by
   an injectable `Clock` for deterministic testing.

## Failover Loop (Day 8)

`GatewayFailoverExecutor` ties routing to the state machine: score →
attempt → on `AUTH_TIMEOUT`/`SERVER_ERROR`/`RATE_LIMITED` (consolidated
onto the same event, see `GatewayOutcomeMapper`), fail over to the next
candidate; on `AUTH_FAILED` (decline) or `AUTH_EXPIRED` (UPI mandate
timeout), stop — no failover (Section A1.1, FS-12).

## Known Deviations from Literal Spec Wording
- FS-07's "shifts all eligible traffic to UPI" is interpreted as
  cascading failover *within* payment-method-compatible gateways, not a
  literal instruction to route card payments to UPI (which A1.3 makes
  impossible for a real UPI gateway). See ADR-009.