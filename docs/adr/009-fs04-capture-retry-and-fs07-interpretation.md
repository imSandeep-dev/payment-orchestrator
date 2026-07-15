# ADR-009: Capture Retry Implementation and Interpretation


## Decisions

1. **CaptureRetryExecutor closes a real gap**: prior, `capture()`
   called the gateway exactly once — no retry-with-backoff existed despite
   being explicitly required. Implemented with genuine sleeps
   (1s/2s/4s), unlike ADR-002's TIMEOUT handling, because it is
   specifically testing backoff timing behavior, not just a routing
   reaction.

2. **Late-success pattern**: after retry exhaust, a `checkStatus()` poll
   resolves whether the gateway actually processed the capture
   server-side despite our client-side view of failure — exactly as
   specifies.

3. **interpretation**: taken completely literally ("shifts all
   eligible traffic to UPI"), this scenario would require routing a
   CREDIT_CARD payment to the UPI gateway during cascade failure — which
   contradicts ("UPI is only via UPI gateway") and our own
   hard payment-method filter (ADR-003 #3). A real UPI gateway
   cannot process a card payment. We test underlying intent —
   exclusion of a down gateway, deprioritization of a degraded one, and
   selection of the healthiest eligible option — within the actual
   payment-method-compatible set, rather than reintroducing a real
   correctness bug to satisfy the scenario's literal prose.

## Consequences
- `TransactionService.capture()`'s behavior is unchanged for every
  existing passing test (all use `MockInstruction.success()`, which
  succeeds on the first retry attempt with zero added delay).
- `docs/state-machine.md` and `docs/adr/003-gateway-router-scoring.md`
  remain the source of truth for payment-method routing rules; it is
  tested against that established design, not against a literal reading
  that would require changing it.