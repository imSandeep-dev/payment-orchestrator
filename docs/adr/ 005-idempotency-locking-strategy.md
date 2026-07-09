# ADR-005: Idempotency Locking Strategy and Discard-and-Retry Policy


## Decisions

1. **Advisory lock (`pg_advisory_xact_lock`) over `SELECT FOR UPDATE`.**
    lists both as valid ("Database-level advisory lock
   OR SELECT FOR UPDATE"). `SELECT FOR UPDATE` requires an existing row;
   the very first request for a brand-new key has none yet. An advisory
   lock exists independently of any row, matching own example.

2. **Merchant-scoped composite key `(merchant_id, idempotency_key)`**.

3. **Two deliberate strengthening beyond literal pseudocode:**
    - **Expired keys are discarded and treated as fresh at READ time**,
      not only via the background cleanup. Without this, a key past its
      24h TTL would remain stuck deduping until that job eventually runs.
    - **A key reused with a different request payload is explicitly
      rejected** (`KEY_REUSED_DIFFERENT_PAYLOAD`), rather than silently
      returning the original cached response for what could be a
      completely different (and possibly client-buggy) request.

## Consequences
- `IdempotencyServiceIT`'s concurrency test uses two real threads with
  independent Spring-managed transactions — not a single-threaded
  approximation — directly satisfying "must be demonstrated with
  a concurrent test" requirement.
- A proper scheduled cleanup job for `idempotency_keys`; today's read-time discard prevents it from being a
  *correctness* gap in the meantime, only a storage-growth one.