# ADR-003: Gateway Router Scoring — Data Granularity and Exclusion Rules


## Decisions

1. **Payment-method granularity for historical seed data.** dataset
   has no payment-method breakdown, though `gateway_health_metrics` has a
   `payment_method` column and wants circuit breakers per-gateway
   *and* per-payment-method. Seeded under sentinel `'ALL'`; used as
   cold-start fallback when no live data exists for a specific pair.

2. **OPEN circuit breaker is a hard exclusion, computed before
   normalization.**: "No requests routed" — an additive weighted
   formula with only 15% health weight can't express that. `GatewayRouter`
   filters OPEN gateways out *before* computing the min-max baseline, so
   an excluded gateway's numbers can't skew the remaining candidates.

3. **UPI payment-method fit is a hard binary filter**, not a soft
   FitScore contribution: "UPI is only via UPI gateway."

4. **Live metrics are in-memory only as of Day 7.** Persisting rolled-up
   windows for the metrics API endpoint is deferred.

## Consequences
- A brand-new gateway with zero data defaults to "assumed healthy" rather
  than being penalized.
- `GatewayRouterTest`'s seven cases hand-verify the formula, including the
  degraded-gateway gap-threshold rule.