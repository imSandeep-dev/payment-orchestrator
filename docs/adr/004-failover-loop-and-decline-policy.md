# ADR-004: Failover Loop Semantics and a Concurrency Scope Boundary


## Decisions

1. **Decline (AUTH_FAILED) never triggers cross-gateway failover.** Section
   A1.1: a decline is a bank decision, not a gateway health problem. The
   loop stops at AUTH_FAILED, deliberately NOT auto-transitioning to
   FAILED — that decision is left to a higher layer, preserving
   the FSM's existing AUTH_FAILED → RETRY_ROUTE path for customer-initiated retries with a different payment method.

2. **UPI mandate expiry (AUTH_EXPIRED) never retries**, per FS-12's
   explicit instruction — this falls out naturally since AUTH_EXPIRED is
   already a terminal state.

3. **MAX_GATEWAY_ATTEMPTS = 3.** With UPI payment method routed exclusively
   to the UPI gateway, 3 covers every non-UPI gateway,
   so this cap never actually truncates a legitimate attempt sequence.

4. **Known scope boundary: allowRequest() is not re-checked per attempt
   inside the failover loop.** GatewayRouter already excludes OPEN
   circuits at selection time. A HALF_OPEN gateway's single test
   slot is only genuinely contested under concurrent overlapping requests
   from SEPARATE transactions — a real concern (similar flavor to FS-09),
   but out of scope for this single-threaded, single-transaction loop.
   Tightening this is future work once TransactionService
   handles concurrent transaction processing for real.

## Amendment: Test isolation for HTTP-based @SpringBootTest classes

RoutingConfigControllerIT initially failed intermittently depending on
JUnit's (unspecified) test method execution order: a PUT in one test
method committed a real change to the database via a genuine HTTP
round-trip to the running server, which a later GET test then observed
instead of the originally seeded values.

Root cause: @DataJpaTest's automatic per-test transaction rollback (used
since Day 4) only works when the test talks to the repository/service
layer directly, inside its own transaction. A @SpringBootTest hitting a
real HTTP endpoint runs the request on a SEPARATE server thread with its
own transaction, which commits before the response even returns — there
is nothing left for the test method's transaction to roll back.

Fix: added a @BeforeEach that resets RoutingConfig to its V11-seeded
values before every test method, making each test correct regardless of
execution order.

Consequence for later days: any @SpringBootTest exercising the API over
real HTTP against a shared Test containers instance (this pattern will
recur heavily in Day 11-12's full API layer, and in webhook ingestion
tests) needs the same explicit state-reset discipline — @Transactional
rollback alone will not protect these tests.