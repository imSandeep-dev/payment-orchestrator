# ADR-001: Language, Framework, and Version Choice

## Status
Accepted — 2026-07-05

## Context
The project spec (Section E1) allows Python/FastAPI, Node.js/TypeScript, or
Java/Kotlin with Spring Boot. We need a stack that supports: a strict
transaction state machine, circuit breakers, distributed locking, connection
pooling under load, and a mature testing story — all within a 15-day window.

## Decision
- **Language:** Java 21 (LTS)
- **Framework:** Spring Boot 4.1.0 (Spring Framework 7, Jakarta EE 11)
- **Build tool:** Maven

Spring Boot is the dominant stack in real-world payment/fintech backends
(Section E4 links Razorpay/Stripe engineering resources that mirror this
choice), and its ecosystem (Resilience4j for circuit breakers, HikariCP for
pooling, Spring Data JPA + Flyway for schema-as-code) maps almost 1:1 onto
the spec's required components.

## Version note
As of July 2026, Spring Boot 3.x has reached full end-of-life (final patch
3.5.16, June 25 2026). We are building on the current supported line,
Spring Boot 4.1.0, rather than an EOL framework. This is a materially
different major version from most existing tutorials/StackOverflow answers,
with real breaking changes we must track through the project:

- **Java 21 minimum** (not 17) — virtual threads available if useful later.
- **Modular starters** — we're using `spring-boot-starter-classic` /
  `spring-boot-starter-test-classic` (a transition bundle Spring provides)
  to avoid hunting for dozens of granular module names on Day 1. We may
  migrate to fine-grained starters later if it's instructive to do so.
- **Jackson 3** — default JSON serialization behavior for dates/BigDecimal
  differs subtly from Jackson 2. We must verify this explicitly once we
  serialize monetary `amount` fields (Day 3+).
- **`@MockBean`/`@SpyBean` removed** — replaced by `@MockitoBean`/
  `@MockitoSpyBean`. Relevant from Day 3 onward once we write service-layer
  unit tests with mocked dependencies.
- **Spring Security 7 (if/when we add it)** — lambda DSL only,
  `authorizeRequests()` is gone in favor of `authorizeHttpRequests()`.
  Not yet relevant (API-key auth arrives Day 11-12) but noted for later.

## Alternatives Considered
- **Python/FastAPI** — faster to prototype, excellent async support, but
  Java's ecosystem for circuit breakers/connection pooling under the
  concurrency stress tested in FS-07/FS-09/FS-14 is more battle-tested.
- **Node.js/TypeScript** — good I/O concurrency model, but weaker built-in
  tooling for the pessimistic-locking + advisory-lock patterns required
  in Section A8.

## Consequences
- Team (i.e., me) must track Boot 4 migration guide gotchas throughout
  the 15 days rather than relying purely on Boot 3.x-era muscle memory.
- Resume value is strong: current Spring Boot + Java 21 + payments-domain
  patterns is directly relevant to fintech/banking engineering roles.