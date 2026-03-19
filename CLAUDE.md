# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build all modules from project root
mvn clean install -DskipTests

# Build a single service
mvn clean package -DskipTests -pl :auth-service

# Run all unit tests
mvn test

# Run tests for a single service
cd auth-service && mvn test

# Run a single test class
mvn test -Dtest=AuthApplicationServiceTest -pl :auth-service

# Run integration tests (TestContainers required)
mvn verify -pl :auth-service
```

## Docker & Infrastructure

```bash
# Start backing infrastructure only (PostgreSQL, MongoDB, Redis, RabbitMQ, MailHog)
cd infrastructure/docker-compose
docker-compose --profile infra up -d

# Add platform services (Config Server, Eureka, Gateway)
docker-compose --profile platform up -d

# Add all microservices
docker-compose --profile services up -d

# Add observability stack (Prometheus, Grafana, Loki, Tempo)
docker-compose --profile observability up -d

# Everything at once
docker-compose --profile all up -d
```

## Architecture

This is a **Maven multi-module** project with 10 Spring Boot services. Every service shares the same layered structure and conventions.

### Request Flow
```
Client → Gateway (8080) → [JWT validation, rate limiting, circuit breaker] → Service
```
The Gateway validates JWTs against auth-service's JWKS endpoint (`/auth/v1/.well-known/jwks.json`). Downstream services trust that the gateway has already validated the token.

### Service-to-Service Communication
- **Synchronous:** OpenFeign clients with Resilience4j circuit breaker fallbacks. Internal endpoints follow the pattern `/users/v1/internal/{id}` (not routed through the gateway).
- **Asynchronous:** RabbitMQ via the **transactional outbox pattern** — the publishing service saves a domain event + outbox record in the same transaction; a scheduler polls the outbox and publishes to RabbitMQ; consumers use manual ACK.

### Key Event Flows
- `auth-service` publishes `UserRegisteredEvent` → consumed by `user-service` (creates profile) and `notification-service` (sends welcome email)
- `payment-service` publishes `PaymentCompletedEvent` → consumed by `course-service` (enrollment) and `notification-service` (sends receipt)

## Per-Service Package Structure (Hexagonal Architecture)

All services follow this layout under `com.lms.<servicename>`:

```
api/
  controller/          ← REST endpoints
  exception/           ← GlobalExceptionHandler (@RestControllerAdvice)
application/
  dto/request|response/ ← API contracts (records or classes)
  port/                ← Interfaces (abstractions owned by the application)
  service/             ← Use-case orchestration (no business logic)
domain/
  model/               ← Aggregate roots and value objects (no Spring/JPA annotations)
  repository/          ← Port interfaces (domain-owned)
  event/               ← Domain event definitions
infrastructure/
  persistence/
    entity/            ← @Entity classes (separate from domain model)
    mapper/            ← MapStruct mappers (domain ↔ entity)
    adapter/           ← Implements domain repository ports
    repository/        ← Spring Data JPA or MongoRepository interfaces
  messaging/           ← RabbitMQ consumers and publishers
  cache/               ← Redis caching
  security/            ← JWT, Spring Security config
  feign/               ← OpenFeign clients with fallback factories
```

**Critical rule:** The domain model has zero Spring or JPA annotations. All framework coupling lives in the `infrastructure` layer.

## Database Conventions

- **PostgreSQL** — auth, user, progress, payment services; Flyway migrations in `src/main/resources/db/migration/`
- **MongoDB** — course, search services (no JPA, Spring Data MongoDB repositories)
- **Redis** — shared instance for rate limiting (gateway) and caching (user, course services)
- Refresh tokens are stored as **SHA-256 hashes** (raw token never persisted)

## Annotation Processing Order

Lombok must be listed before MapStruct in `maven-compiler-plugin` annotation processor paths — the root POM already enforces this. Do not reorder them.

## Centralized Configuration

All service configs live in `config-server/src/main/resources/configs/<service-name>.yml`. Services fetch these at startup via `spring.config.import`. When adding a new config property to a service, add it to both the config-server file (with environment variable placeholders) and the local `application.yml` (bootstrap only).

## Observability

Every service registers custom Micrometer metrics in its application service layer:
```java
Counter.builder("auth.registrations.total").register(meterRegistry).increment();
```
Distributed traces flow through OpenTelemetry → Tempo. Logs go to Loki via Logback HTTP appender. All are queryable in Grafana (port 3000, `admin`/`lms_grafana_secret`).

## Auth Service Specifics (Port 9000)

- Issues RSA-2048 signed JWTs (access token: 15 min, refresh token: 7 days)
- Refresh tokens are HttpOnly Secure SameSite=Strict cookies
- Token reuse detection: using a revoked refresh token revokes **all** tokens for that user
- Account locking: 5 failed login attempts → 15-minute lock

## Ports Quick Reference

| Service | Port | DB |
|---|---|---|
| Gateway | 8080 | — |
| Auth | 9000 | PostgreSQL `lms_auth` |
| User | 8081 | PostgreSQL `lms_users` |
| Course | 8082 | MongoDB |
| Progress | 8083 | PostgreSQL `lms_progress` |
| Payment | 8084 | PostgreSQL `lms_payments` |
| Notification | 8085 | — (stateless) |
| Search | 8086 | MongoDB |
| Config Server | 8888 | — |
| Eureka | 8761 | — |
| Grafana | 3000 | — |
| MailHog UI | 8025 | — |
