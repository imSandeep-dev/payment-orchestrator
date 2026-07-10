# ADR-007: TransactionService Design Decisions


## Decisions
1. **Audit-log granularity**: one row per real outbound gateway call during
   authorization (not per FSM micro-transition) — see TransactionService's
   class-level Javadoc for full reasoning.
2. **Idempotency "failure" = unexpected exception, not business decline.**
   A declined card is a valid, cacheable COMPLETED outcome; only genuine
   processing exceptions mark the key FAILED.
3. **KNOWN GAP: no VOID_FAILED state.** The locked 24-state design has no failure state for VOID_INITIATED. A failed void
   currently parks the transaction there and surfaces a 502 to the caller.
   Properly fixing this needs a new state + migration + CHECK constraint
   update and dedicated tests — deferred as a backlog item rather than
   destabilizing the transition table mid-API-layer-day.
4. **Uniform mock-instruction map.** Section B4.3's X-Mock-* headers
   describe one signal per request; PaymentController applies it uniformly
   across all four known gateway names so GatewayFailoverExecutor's per-gateway lookup works unmodified regardless of which gateway wins
   routing.
5. **API-key authentication middleware — merchant_id is an explicit query
   parameter.
6. **Webhook payload shape is our own mock convention**, not a faithful
   reproduction of each real gateway's native JSON — we're testing it against
   our own mock gateways, not real Razorpay/Stripe/PayU/UPI endpoints.

## Addendum: void's failure path required splitting into independent transactions

Initial voidAuthorization() wrapped the VOID_INITIATED transition, the
gateway call, and (on failure) the error log writes all inside one
@Transactional method that then threw ApiException. Since any unchecked
exception rolls back the WHOLE transaction, the VOID_INITIATED write and
the failure log was both silently undone — the transaction reverted to
its pre-void state entirely, contradicting the "known gap" behavior this
was meant to document.

Fixed by extracting VoidLifecycleRecorder with
three independent @Transactional methods, each committing before the next
step runs. This also brings void in line with Section A8.1's own
recommended pattern of not holding one transaction open across an
external gateway call — the bug fix and the spec-recommended design
turned out to be the same fix.