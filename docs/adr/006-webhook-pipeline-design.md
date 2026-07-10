# ADR-006: Webhook Pipeline Design — Dedup Technique, Catch-Up Chains, Scope Boundaries


## Decisions

1. **Dedup via unique-constraint-and-catch, NOT an advisory lock** (contrast
   with IdempotencyService). A duplicate webhook has no "in-flight"
   state to wait on — it's either already processed or it isn't. Relying
   on `processed_webhook_events`'s composite PK and catching
   `DataIntegrityViolationException` is simpler and equally correct for
   this specific concurrency shape.

2. **FS-06 solved via catch-up transition chains, not a new FSM edge.**
   `WebhookEventMapper.chainFor()` walks real intermediate states when a
   webhook implies we're behind, producing one audit log entry per actual
   transition rather than one fabricated skip-edge.

3. **`PAYMENT_REVERSED` maps to `RECONCILIATION_OVERRIDE`**, following
   Case Study C2's lesson directly: a post-capture reversal must become
   `RECONCILIATION_MISMATCH` for human review, never a silent state change
   or an automatic refund.

4. **UPI signature verification is HMAC, not real NPCI PKI** — an explicit,
   documented simplification (see `UpiWebhookSignatureVerifier`'s Javadoc).
   A faithful implementation needs `java.security.Signature` with
   asymmetric keys and X.509 certificate handling, impractical without a
   real NPCI-issued certificate in a training environment.

5. **Scope boundary: no real HTTP endpoints yet.** 
   `WebhookEventProcessor` is called directly with a pre-built
   `IncomingWebhookRequest`. The actual `POST /api/v1/webhooks/{gateway}`
   controllers — including translating each gateway's native event
   vocabulary into `WebhookEventType`, and source-IP security audit
   log — arrive.

## Consequences
- Two different, individually justified concurrency techniques exist in
  the same codebase — a
  deliberate choice per problem shape, not an inconsistency.
- The "late API response arrives after a webhook already advanced the
  transaction" half (the state machine correctly rejecting the
  stale transition) is structurally guaranteed by `TransactionStateMachine`
  itself — but the caller that would need to catch and gracefully ignore
  that exception doesn't exist until `TransactionService`.

## Addendum: REQUIRES_NEW requires a separate bean, not just a separate method

First attempt put @Transactional(REQUIRES_NEW) directly on
tryMarkProcessed() and caught the exception inline. This still threw
UnexpectedRollbackException: repository.saveAndFlush() is itself
@Transactional(REQUIRED) internally, so it JOINED tryMarkProcessed()'s
already-open REQUIRES_NEW transaction rather than starting a new one. On
the duplicate-key violation, saveAndFlush()'s advice (a non-owning
participant in that shared transaction) could only mark it rollback-only
and rethrow — it could not roll back that connection itself, since it
didn't open it. Catching the exception in tryMarkProcessed() didn't
un-mark that flag, so its own advice (the actual owner) threw
UnexpectedRollbackException on commit.

Fixed by moving the insert into ProcessedWebhookEventInserter, a genuinely
separate bean. Its own REQUIRES_NEW transaction is the one that owns and
cleanly rolls back on failure, and the exception is caught in
WebhookDedupService.tryMarkProcessed(), which has no transactional
boundary of its own to be affected.