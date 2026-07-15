# ADR-008: Reconciliation Engine and Auth Middleware Decisions


## Decisions
1. checkStatus() is a PaymentGateway default method — identical mock
   simulation across all four gateways, unlike authorize/capture/etc.
2. Stale-transaction recovery auto-resolves either direction (success or
   failure); settlement-mismatch detection NEVER auto-resolves.
3. checkStatus() collapses all non-SUCCESS response types to "FAILED" —
   enough to exercise match/mismatch branching without a second full
   mock-response taxonomy for settlement states specifically.
4. ApiKeyAuthFilter authenticates (valid key present) but does not yet
   propagate merchantId into controller signatures — PaymentController
   still takes merchantId as an explicit parameter (per ADR-007 #5).
5. Added PARTIALLY_CAPTURED → RECONCILIATION_MISMATCH transition — an
   oversight found while building the settlement check; see
   docs/state-machine.md Amendments.
6. api-specification.yaml covers all 23 paths structurally; only 2 are
   fully detailed with request/response schemas in this pass — the remainder 
   follow identical conventions, expandable via Swagger Editor (E3).