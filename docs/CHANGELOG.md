# Changelog

All notable changes to the Payment Orchestrator project.

## Project Bootstrap
Spring Boot 4.1.0 skeleton, Docker Compose (PostgreSQL), DB-backed health check.

## Database Schema
11-table PostgreSQL schema via Flyway, `docs/state-machine.md` (24 states,
39 transitions), `docs/architecture.md` first draft, ADR-001.
Fixed: Flyway/PostgreSQL dependency gap (`flyway-database-postgresql`).

## Transaction State Machine
`TransactionStateMachine` — data-driven transition table, 61 tests
(39 valid + 17 invalid + 5 targeted).

## Persistence Layer
JPA entities (`Transaction`, `TransactionStateLog`) with no-naked-setter
discipline, `GatewayResponseSanitizer` (PII redaction), Testcontainers-backed
integration tests.

## Gateway Adapters
`PaymentGateway` interface, mock simulation engine (Template Method),
all 4 adapters (Razorpay, Stripe, PayU, UPI). `GatewayOutcomeMapper`.
Fixed: state machine gap — added `MANDATE_EXPIRED` event (FS-12).

## Routing & Circuit Breaking
`GatewayHealthMetrics`, `GatewayRouter` (A3.2 scoring), `CircuitBreaker`
(injectable Clock), `GatewayFailoverExecutor`, routing config API.

## Idempotency & Webhooks
`IdempotencyService` (advisory locking, FS-03/09/13), full webhook pipeline
(signature verification, dedup, event processor, FS-06 catch-up chains).
Fixed: `Persistable`/merge-vs.-persist bug, transaction-propagation
(`UnexpectedRollbackException`) bug.

## API Layer & Reconciliation
`TransactionService`, standard error envelope, all 23 REST endpoints,
`ReconciliationEngine`, API-key auth.
Fixed: void's failure-path transaction rollback bug (`VoidLifecycleRecorder`).
State machine fix: `PARTIALLY_CAPTURED → RECONCILIATION_MISMATCH`.

## Failure Scenario Validation
`CaptureRetryExecutor` (closed a real FS-04 gap — capture had no retry
logic this). End-to-end tests + coverage matrix for all 15
failure scenarios. JaCoCo wired in.

## Docker & Performance
Full Docker Compose (app + Postgres), Failsafe wiring (`mvn verify` runs
all integration tests), HikariCP tuning, k6 load test, idempotency key
cleanup job, finalized documentation.

## Final Polish
Checkstyle linting, code cleanup, clean-state Docker re-verification,
error-detection writeup, this changelog, repository transfer.

## Final Stats
- 217 tests (155 unit/fast-integration + 62 Testcontainers integration)
- 23 REST API endpoints
- 9 ADRs
- 15/15 failure scenarios covered (see `FailureScenarioEndToEndIT`'s matrix)
- Three real production-grade bugs found and fixed during development
  (documented in ADRs and this changelog)