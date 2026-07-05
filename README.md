# Payment Orchestrator

Payment Orchestration Layer with Multi-Gateway Failover — PayFlow Commerce simulation.

## Prerequisites
- JDK 21
- Maven 3.9+
- Docker Desktop 24+

## Running locally

1. Copy environment config:
   cp .env.example .env

2. Start PostgreSQL:
   docker-compose up -d

3. Confirm Postgres is healthy:
   docker ps   # STATUS should show "healthy"

4. Run the application:
   mvn spring-boot:run

5. Verify:
   curl http://localhost:8080/api/v1/health

Expected response:
{"status":"UP","service":"payment-orchestrator","timestamp":"...","components":{"database":"UP"}}

## Running tests
Requires Postgres running (step 2 above):
mvn test