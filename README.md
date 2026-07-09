# Payment Orchestrator

Payment Orchestration Layer with Multi-Gateway Failover — PayFlow Commerce simulation.

## Stack

Java 21 · Spring Boot 4.1.0 · PostgresSQL 15 · Flyways · Spring Data JPA · Test containers

See `docs/adr/001-language-and-framework-choice.md` for the full rationale, including
Spring Boot 4-specific notes (Jackson 3, modular starters, `@MockitoBean`).

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop 24+ (required both for local Postgres AND for running integration tests,
  which use Test containers to spin up a real disposable Postgres instance)

## Running Locally

1. Copy environment config:cp .env.example .env
2. Start PostgresSQL: docker compose up -d
3. Run the application (Flyway migrations apply automatically on startup):mvn spring-boot:run
4. Verify:
   curl http://localhost:8080/api/v1/health
   Expected response:

    ```json
       {"status":"UP","service":"payment-orchestrator","timestamp":"...","components":{"database":"UP"}}
    ```
    
## Running Tests
    
Docker Desktop must be running — integration tests use Test containers, not the
`docker-compose` Postgres instance directly.
    
mvn test # full suite
- mvn test -Dtest=TransactionStateMachineTest # state machine only (fast, no DB)
- mvn test -Dtest=GatewayResponseSanitizerTest # PII redaction only (fast, no DB)
- mvn test -Dtest=TransactionPersistenceIT # persistence layer (needs Docker)
- mvn test "-Dtest=RazorpayAdapterTest, StripeAdapterTest" # gateway adapter contract tests (fast, no DB)
- mvn test "-Dtest=PayUAdapterTest,UPIAdapterTest"
- mvn test -Dtest=GatewayToStateMachineIntegrationTest
- mvn test -Dtest=GatewayHealthMetricsSeedDataIT
- mvn test -Dtest=GatewayRouterTest
- mvn test -Dtest=CircuitBreakerTest # 9 tests, fast, no DB
- mvn test -Dtest=GatewayFailoverExecutorTest # 6 tests, fast, no DB
- mvn test -Dtest=RoutingConfigControllerIT # 3 tests, needs Docker

## Project Structure

The project follows a clean, layered architecture:

```text
src/main/java/com/payflow/orchestrator/
├── config/             # Spring configuration (Security, Thread pools)
├── controller/         # REST API endpoints
├── domain/             # Entities, State Machine, Value Objects
├── exception/          # Global error handling
├── gateway/            # Integration logic for external payment providers
├── repository/         # Spring Data JPA interfaces
├── service/            # Business logic and coordination
├── util/               # Shared utilities (e.g., PII Sanitizer)
└── webhook/            # Asynchronous webhook ingestion

## Documentation

- `docs/architecture.md` — system design, component map
- `docs/state-machine.md` — full 24-state, 39-transition FSM specification
- `docs/adr/` — architecture decision records