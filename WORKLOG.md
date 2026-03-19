# LMS Microservices Platform — Work Log

> **How this file works:** Updated by Claude Code after each task session.
> Tracks what was built, what was fixed, and what comes next.

---

## Project Overview

**Stack:** Java 21 · Spring Boot 3.3.4 · Spring Cloud 2023.0.3
**Databases:** PostgreSQL (auth, users, progress, payments) · MongoDB (courses, search)
**Messaging:** RabbitMQ (transactional outbox pattern)
**Caching:** Redis
**Auth:** RSA-2048 signed JWTs (JJWT 0.12.6)
**Observability:** Prometheus · Grafana · Loki · Tempo · OpenTelemetry

---

## Overall Status

| Phase | Status |
|-------|--------|
| Foundation (POM, infra, platform services) | ✅ Complete |
| All 7 microservices | ✅ Complete |
| Docker Compose (all profiles) | ✅ Complete |
| Observability stack | ✅ Complete |
| Profile-based env separation (dev/docker) | ✅ Complete |
| Auth service end-to-end tested & documented | ✅ Complete |
| Kubernetes + Helm | ⬜ Not started |
| GitHub Actions CI/CD | ⬜ Not started |

---

## Services

| Service | Port | DB | Status |
|---------|------|----|--------|
| Gateway | 8080 | — | ✅ |
| Config Server | 8888 | — | ✅ |
| Eureka | 8761 | — | ✅ |
| Auth | 9000 | PostgreSQL `lms_auth` | ✅ |
| User | 8081 | PostgreSQL `lms_users` | ✅ |
| Course | 8082 | MongoDB | ✅ |
| Progress | 8083 | PostgreSQL `lms_progress` | ✅ |
| Payment | 8084 | PostgreSQL `lms_payments` | ✅ |
| Notification | 8085 | — (stateless) | ✅ |
| Search | 8086 | MongoDB | ✅ |

---

## Session Log

### Session 1 — Foundation Layer
**Completed:**
- Root parent POM (`pom.xml`) — Java 21, Spring Boot 3.3.4, Spring Cloud 2023.0.3
- Config Server (`config-server/`) — port 8888, native profile, HTTP Basic secured
- Eureka Server (`eurekaserver/`) — port 8761, HTTP Basic secured (`eureka-admin`/`eureka-secret`)
- Gateway Server (`gatewayserver/`) — port 8080, JWT validation, Redis rate limiting, Resilience4j circuit breakers
- All centralized config files in `config-server/src/main/resources/configs/`
- Docker Compose with profiles: `infra` / `platform` / `services` / `observability` / `all`
- Observability configs: Prometheus + alert rules, Loki, Tempo, Grafana datasource provisioning
- Dockerfiles for config-server, eurekaserver, gatewayserver (multi-stage layered JARs)

---

### Session 2 — Microservices Implementation
**Completed:**
- Auth Service (port 9000) — RSA JWT, refresh token rotation, account locking, outbox pattern
- User Service (port 8081) — profile management, Redis caching, Feign client to auth-service
- Course Service (port 8082) — MongoDB, content management, enrollment via events
- Progress Service (port 8083) — lesson/course completion tracking
- Payment Service (port 8084) — payment processing, enrollment trigger events
- Notification Service (port 8085) — RabbitMQ consumer, MailHog for local email
- Search Service (port 8086) — MongoDB full-text search, event-driven index updates

---

### Session 3 — Docker & Environment Fixes
**Problems solved:**

1. **Gateway `NoUniqueBeanDefinitionException`** — two `RedisRateLimiter` beans and two `KeyResolver` beans caused constructor injection failure.
   - Fix: Added `@Primary` to `redisRateLimiter()` and `ipKeyResolver()` in `RouteConfig.java`.

2. **Eureka registration failures** — only 3 of 7 services appeared in Eureka.
   - Root cause: `auth-service.yml`, `course-service.yml`, `gatewayserver.yml` had Eureka URLs without HTTP Basic credentials. Eureka server rejects all unauthenticated registrations with 401.
   - Fix: Added `eureka-admin:eureka-secret@` credentials to all config files.

3. **Profile-based environment separation** — services needed different hostnames for local IntelliJ vs Docker.
   - Solution: `dev` profile uses `application-dev.yml` with `localhost` overrides; `docker` profile uses defaults (Docker service hostnames). No manual changes needed when switching environments.
   - New files: `configs/application-dev.yml`, `configs/*-dev.yml` per service, `configs/gatewayserver-dev.yml`

4. **auth-service container `unhealthy`** (multiple layers):
   - Stale Docker image — must run `mvn clean package` before `docker compose build`
   - `EurekaHealthIndicator` marking service DOWN → fixed with `management.health.eureka.enabled: false` in `configs/application.yml`
   - `RabbitMQ` defaulting to `localhost:5672` inside container — `spring.rabbitmq` block was accidentally nested under `jwt:` key instead of `spring:`. Fixed indentation.

5. **user-service RabbitMQ misconfiguration** — same `spring.rabbitmq` indentation issue + missing env vars in docker-compose.
   - Fix: Corrected YAML structure in `user-service.yml`, added `RABBITMQ_*` env vars and `depends_on: rabbitmq` to docker-compose.

**Result:** All 7 microservices running `healthy` in Docker, all registered in Eureka.

---

### Session 4 — Auth Service Test Fixes (2026-03-19)
**Problem:** `mvn clean package -DskipTests` failed on test compilation; `mvn test` had errors.

**Errors fixed:**

1. **Constructor mismatch** — `AuthApplicationService` constructor was updated (added `OutboxRepository` + `ObjectMapper` + `MeterRegistry`) but the test still used the old 6-arg signature.
   - Fix: Added `@Mock OutboxRepository outboxRepository` to test; updated constructor call to pass all 8 args.

2. **`LoginResult` vs `AuthResponse`** — `login()` return type changed from `AuthResponse` to `LoginResult` (wraps `AuthResponse` + raw refresh token for cookie).
   - Fix: Changed test variable type to `LoginResult`; access auth data via `result.authResponse()`.

3. **`eventPublisher` verify obsolete** — registration now uses the transactional outbox (saves `OutboxEvent` to DB) instead of calling `eventPublisher` directly.
   - Fix: Replaced `verify(eventPublisher).publishUserRegistered(any())` with `verify(outboxRepository).save(any())`; added `when(outboxRepository.save(any())).thenAnswer(...)` stub.

4. **`Instant` serialization failure** — `new ObjectMapper()` cannot serialize `java.time.Instant` (used in `UserRegisteredEvent`) without `JavaTimeModule`.
   - Fix: `new ObjectMapper().registerModule(new JavaTimeModule())` in test setup.

5. **MapStruct warning** — `RefreshTokenEntity` has `deviceName`, `ipAddress`, `userAgent`, `lastUsedAt` columns (added by V5 migration for device tracking) but `RefreshToken` domain model does not. MapStruct warned about unmapped targets.
   - Fix: Added `@Mapping(target = "...", ignore = true)` for all four fields in `RefreshTokenPersistenceMapper`.

**Result:** All 8 tests pass, build succeeds with no warnings.

---

### Session 5 — Auth Service End-to-End Bug Fixes & Testing (2026-03-19)
**Bugs fixed:**

1. **Gateway container using stale image** — `@Primary` fix was applied to source but Docker image was never rebuilt. Gateway crashed on startup with `NoUniqueBeanDefinitionException`.
   - Fix: `mvn clean package -DskipTests -pl :gatewayserver` → `docker compose build gatewayserver` → restart.

2. **`outbox_events.payload` JSONB type mismatch** — `@Column(columnDefinition = "jsonb")` only affects DDL, not JDBC binding. Hibernate sent `VARCHAR` for the payload; PostgreSQL rejected it.
   - Fix: Added `@JdbcTypeCode(SqlTypes.JSON)` to `OutboxEventEntity.payload`.
   - File: `infrastructure/persistence/entity/OutboxEventEntity.java`

3. **`/auth/v1/logout` returning 403 Forbidden** — endpoint was under `.anyRequest().authenticated()` but auth-service has no JWT validation filter (it's the issuer, not a resource server). Logout only needs the refresh token cookie, not a Bearer token.
   - Fix: Added `/auth/v1/logout` to `permitAll()` in `SecurityConfig.java`.

4. **RabbitMQ messages going to DLQ — double serialization bug** — `OutboxPublisher` called `rabbitTemplate.convertAndSend(..., event.getPayload())` where payload is a `String`. `Jackson2JsonMessageConverter` double-serialized it (JSON string inside JSON string) and set `__TypeId__=java.lang.String`. Consumer received a String value and failed to deserialize into `UserRegisteredEvent`.
   - Fix: Changed to `rabbitTemplate.send()` with `MessageBuilder.withBody(payload.getBytes(UTF_8))` — sends raw JSON bytes without `__TypeId__` header. Consumer infers target type from `@RabbitListener` method parameter.
   - File: `infrastructure/messaging/outbox/OutboxPublisher.java`

**Result:** Full auth workflow verified end-to-end:
- `POST /auth/v1/register` → 201, outbox event published → user-service creates profile → notification-service sends welcome email (MailHog)
- `POST /auth/v1/login` → 200 + JWT + HttpOnly cookie
- `POST /auth/v1/logout` → 200, refresh token revoked

---

## Known Issues / Tech Debt

| # | Issue | Severity | Notes |
|---|-------|----------|-------|
| 1 | `RefreshToken` domain model missing device tracking fields | Low | Entity has `deviceName`, `ipAddress`, `userAgent`, `lastUsedAt` (V5 migration) but domain model doesn't expose them. Mapper ignores them for now. |
| 2 | `eventPublisher` port unused in auth-service | Low | Port interface exists but registration now goes through outbox. Can be removed. |
| 3 | Grafana dashboards not provisioned | Medium | Datasources are provisioned (Prometheus, Loki, Tempo) but dashboards are manual. |

---

## Pending Work

### Next up
- [ ] Kubernetes manifests (Deployments, Services, ConfigMaps, Secrets)
- [ ] Helm chart for the full platform
- [ ] GitHub Actions CI/CD pipeline (build → test → push image → deploy)

### Backlog
- [ ] Add device tracking to `RefreshToken` domain model (align with V5 DB schema)
- [ ] Integration tests with Testcontainers for auth-service
- [ ] Grafana dashboards — provision JSON dashboards for each service
- [ ] Test remaining services end-to-end (user, course, payment, progress, search)

---

## Architecture Quick Reference

```
Client → Gateway (8080)
           ├── JWT validation (JWKS from auth-service:9000)
           ├── Rate limiting (Redis token bucket — 20 req/s, burst 40)
           └── Circuit breakers (Resilience4j)
                    ↓
         Microservices (via Eureka discovery, lb://)
```

**Event flows:**
- `auth-service` → `UserRegisteredEvent` → `user-service` (create profile) + `notification-service` (welcome email)
- `payment-service` → `PaymentCompletedEvent` → `course-service` (enroll) + `notification-service` (receipt)

**Config hierarchy** (last wins):
```
application.yml → application-{profile}.yml → {service}.yml → {service}-{profile}.yml
```

**Build → Docker pipeline (MUST follow this order):**
```bash
mvn clean package -DskipTests -pl :<service-name>  # 1. recompile JAR
docker compose build <service-name>                 # 2. bake new image
docker compose --profile services up -d <service>  # 3. restart container
```
