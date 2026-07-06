# Architecture — Payment Orchestration Layer

*Status: first draft (Day 2). Finalized on Day 14.*

## Stack
Java 21, Spring Boot 4.1.0, PostgreSQL 15. See ADR-001 for rationale.

## Component Map

​```mermaid
flowchart TB
    Client[Merchant Client] -->|REST| API[API Layer]
    API --> Idem[Idempotency Service]
    API --> FSM[Transaction State Machine]
    FSM --> Router[Gateway Router + Circuit Breaker]
    Router --> Adapters[Gateway Adapters: Razorpay/Stripe/PayU/UPI mocks]
    Gateways[Mock Gateways] -->|webhook| Webhook[Webhook Ingestion Pipeline]
    Webhook --> Dedup[Dedup Layer]
    Dedup --> FSM
    Recon[Reconciliation Engine] -->|polls status| Adapters
    Recon --> FSM
    FSM --> DB[(PostgreSQL)]
    Idem --> DB
    Webhook --> DLQ[(webhook_queue DLQ)]
​```

## Components & Responsibilities
- **API Layer** (Day 11-12): 23 REST endpoints per A7.1, standard error envelope per A7.2.
- **Idempotency Service** (Day 9-10): merchant-scoped key locking (A4, FS-03, FS-09, FS-13).
- **Transaction State Machine** (Day 3-4): enforces the transition table in `state-machine.md`.
- **Gateway Router + Circuit Breaker** (Day 7-8): multi-criteria scoring (A3.2) + 3-state breaker (A3.3), per-gateway *and* per-payment-method.
- **Gateway Adapters** (Day 5-6): uniform interface over 4 mock gateways, driven by `X-Mock-*` headers (B4.3).
- **Webhook Pipeline** (Day 9-10): signature verification → dedup → queue → FSM transition (A5.2-A5.4).
- **Reconciliation Engine** (Day 11-12): stale-transaction detection + settlement comparison (A5.5).

## Key Design Decisions (see full rationale in migration file comments)
1. Money as `BIGINT` paise, never float (A6.2).
2. State stored as `VARCHAR + CHECK`; enforcement lives in application code.
3. Idempotency keys scoped `(merchant_id, key)` — required by FS-13.
4. `webhook_queue` DLQ table added beyond the 10-table minimum (A8.3).
5. Application-generated UUIDs (no DB extension dependency).

## Open Items for Later Days
- Pessimistic vs optimistic locking implementation details → Day 7-8/9-10 (A8.1)
- PII redaction function for `gateway_response` JSONB → Day 3-4
- Rate limiter design (token bucket vs sliding window) → Day 7-8/8.4 (deferred beyond core 15-day path unless time permits)