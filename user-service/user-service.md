# User Service

Manages user profile data across the LMS platform. Created automatically when a user registers via auth-service (event-driven), and serves as the source of truth for identity display across all services.

- **Port:** `8081`
- **Database:** PostgreSQL (`lms_users`)
- **Cache:** Redis (`user-profiles` cache, 10-minute TTL)
- **Messaging:** RabbitMQ consumer (`lms.user.user-registered` queue)
- **Access:** Via Gateway (`http://localhost:8080/users/v1/...`)

---

## Table of Contents

1. [Architecture](#architecture)
2. [API Endpoints](#api-endpoints)
3. [Authentication & Authorization](#authentication--authorization)
4. [Event-Driven Profile Creation](#event-driven-profile-creation)
5. [Redis Caching Strategy](#redis-caching-strategy)
6. [Feign Client & Fallback](#feign-client--fallback)
7. [Database Schema](#database-schema)
8. [Observability](#observability)
9. [Running Locally](#running-locally)

---

## Architecture

Follows **Hexagonal Architecture** (Ports & Adapters) — domain has zero Spring/JPA annotations.

```
com.lms.userservice
│
├── api/
│   ├── controller/UserController.java          ← REST endpoints + ownership enforcement
│   └── exception/GlobalExceptionHandler.java   ← RFC 7807 ProblemDetail responses
│
├── application/
│   ├── dto/request/UpdateProfileRequest.java
│   ├── dto/response/UserProfileResponse.java   ← Stable contract used by Feign clients
│   ├── dto/response/PageResponse.java          ← Generic pagination wrapper
│   └── service/UserProfileService.java         ← Use-case orchestration + cache annotations
│
├── domain/
│   ├── model/UserProfile.java                  ← Aggregate root (business rules here)
│   ├── repository/UserProfileRepository.java   ← Port interface (domain-owned)
│   └── exception/
│       ├── UserProfileNotFoundException.java
│       └── UserProfileAlreadyExistsException.java
│
└── infrastructure/
    ├── persistence/
    │   ├── entity/UserProfileEntity.java        ← @Entity (separate from domain)
    │   ├── mapper/UserProfilePersistenceMapper  ← MapStruct (entity ↔ domain)
    │   ├── adapter/UserProfileRepositoryAdapter ← Implements repository port
    │   └── repository/JpaUserProfileRepository  ← Spring Data JPA
    ├── messaging/
    │   ├── RabbitMQConfig.java                  ← Queue, exchange, DLQ topology
    │   ├── UserRegisteredEvent.java             ← Local copy of auth-service event
    │   └── UserRegisteredEventConsumer.java     ← @RabbitListener with manual ACK
    ├── security/
    │   └── SecurityConfig.java                  ← JWT resource server + role mapping
    ├── cache/
    │   └── CacheConfig.java                     ← Redis + JSON serialization config
    └── feign/
        ├── UserServiceFeignClient.java           ← Client interface for other services
        └── UserServiceFeignClientFallbackFactory ← Resilience4j fallback
```

### Data Flow Overview

```
POST /auth/v1/register
        │
        ▼ (auth-service, via outbox)
RabbitMQ: lms.auth.events / lms.auth.user.registered
        │
        ▼
UserRegisteredEventConsumer (MANUAL ACK)
        │
        ▼
UserProfileService.createProfile()   ← idempotent: skips if userId already exists
        │
        ▼
UserProfileEntity saved → PostgreSQL (lms_users)

────────────────────────────────────────────────

GET /users/v1/me  (with Bearer JWT)
        │
        ▼ Gateway validates JWT via JWKS
        ▼
UserController.getMyProfile()
        │
        ▼ @Cacheable("user-profiles", key="#userId")
   Redis HIT? ──── YES ──► return cached UserProfileResponse
        │
       NO
        ▼
UserProfileRepository.findByUserId() → PostgreSQL
        │
        ▼
Store in Redis (TTL 10 min) → return response
```

---

## API Endpoints

All routes accessed through Gateway on port `8080`. Require valid Bearer JWT except where noted.

### User Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/users/v1/me` | Authenticated | Get your own profile |
| `PUT` | `/users/v1/me` | Authenticated | Update your own profile |
| `GET` | `/users/v1/{userId}` | Authenticated (own) or ADMIN | Get a profile by userId |
| `PATCH` | `/users/v1/{userId}` | ADMIN only | Admin update any profile |
| `GET` | `/users/v1/internal/{userId}` | Internal network only | Feign client endpoint (no JWT) |

### Admin Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/users/v1/admin/users` | ADMIN | List all users (paginated) |
| `GET` | `/users/v1/admin/users/role/{role}` | ADMIN | List users by role |
| `POST` | `/users/v1/admin/users/{userId}/suspend` | ADMIN | Suspend a user |
| `POST` | `/users/v1/admin/users/{userId}/reactivate` | ADMIN | Reactivate a suspended user |

### Get My Profile

```http
GET /users/v1/me
Authorization: Bearer <access_token>
```

**Response `200 OK`:**
```json
{
  "userId": "c89c5805-0106-4525-89d8-3942960166b3",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "fullName": "John Doe",
  "bio": null,
  "avatarUrl": null,
  "role": "STUDENT",
  "status": "ACTIVE",
  "createdAt": "2026-03-19T08:56:39Z"
}
```

### Update My Profile

```http
PUT /users/v1/me
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Smith",
  "bio": "Software developer learning microservices",
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

All fields are optional — only provided fields are updated.

### List All Users (Admin)

```http
GET /users/v1/admin/users?page=0&size=20&sortBy=createdAt&direction=DESC
Authorization: Bearer <admin_access_token>
```

**Response `200 OK`:**
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

---

## Authentication & Authorization

User-service is an **OAuth2 Resource Server** — it validates JWTs independently using the same JWKS endpoint as the gateway.

```yaml
# application.yml (bootstrap)
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://auth-service:9000/auth/v1/.well-known/jwks.json
```

### Role Extraction

The JWT contains `"role": "ADMIN"`. Spring Security expects `"ROLE_ADMIN"` prefix. The converter bridges this:

```java
converter.setJwtGrantedAuthoritiesConverter(jwt -> {
    String role = jwt.getClaimAsString("role");
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
});
```

This enables `@PreAuthorize("hasRole('ADMIN')")` on controller methods.

### Ownership Enforcement

`GET /users/v1/{userId}` enforces that users can only read their own profile:
```java
UUID requestingUserId = UUID.fromString(jwt.getSubject()); // "sub" claim
String role = jwt.getClaimAsString("role");
if (!requestingUserId.equals(userId) && !"ADMIN".equals(role)) {
    return ResponseEntity.status(403).build();
}
```

### Internal Endpoint

`GET /users/v1/internal/{userId}` has **no authentication** — it's only accessible within the Docker/Kubernetes network. The gateway never routes `/internal/**` paths to external clients.

---

## Event-Driven Profile Creation

### Why event-driven instead of a direct call?

If auth-service called user-service directly (REST/Feign) to create the profile at registration time:
- Auth-service would be **tightly coupled** to user-service availability
- Registration would fail if user-service is down
- No retry mechanism without complex orchestration

With the event-driven approach via RabbitMQ:
- Auth-service registers the user and publishes an event — done
- User-service creates the profile asynchronously
- If user-service is down, the message waits in the queue and is processed on recovery

### Consumer Implementation

```java
@RabbitListener(queues = "lms.user.user-registered")
public void handleUserRegistered(UserRegisteredEvent event, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    try {
        userProfileService.createProfile(event.userId(), event.email(),
                event.firstName(), event.lastName(), event.role());
        channel.basicAck(deliveryTag, false);          // SUCCESS: remove from queue

    } catch (Exception e) {
        boolean requeue = isTransientError(e);
        channel.basicNack(deliveryTag, false, requeue); // FAIL: retry or DLQ
    }
}
```

### Acknowledgement Strategy

| Error Type | Action | Result |
|---|---|---|
| Success | `basicAck` | Message removed from queue |
| Profile already exists | `basicAck` | Idempotent — safe to discard |
| DB connection lost (`TransientDataAccessException`) | `basicNack(requeue=true)` | Message requeued for retry |
| Permanent failure (bad data) | `basicNack(requeue=false)` | Message goes to `lms.user.dlq` |

### Idempotency

```java
if (userProfileRepository.existsByUserId(userId)) {
    // RabbitMQ may redeliver on crash — handle gracefully
    return existingProfile; // Don't throw, don't duplicate
}
```
RabbitMQ guarantees **at-least-once** delivery. The consumer handles this by checking existence before creating.

### Dead Letter Queue

Failed messages (after exhausting retries) go to `lms.user.dlq`. Configuration:
```java
QueueBuilder.durable("lms.user.user-registered")
    .withArgument("x-dead-letter-exchange", "lms.user.dlq.exchange")
    .withArgument("x-dead-letter-routing-key", "lms.user.dlq")
    .withArgument("x-message-ttl", 300_000)  // 5 min max in queue
    .build();
```

---

## Redis Caching Strategy

### What is cached

`GET /users/v1/{userId}` and `GET /users/v1/me` are cached because:
- Profile data changes rarely (user updates their own profile infrequently)
- Profile reads are **very frequent** — every course enrollment check, progress lookup, and admin list hits this

### Cache operations

```java
@Cacheable(value = "user-profiles", key = "#userId")    // GET: cache or DB
public UserProfileResponse getProfile(UUID userId) { ... }

@CacheEvict(value = "user-profiles", key = "#userId")   // UPDATE: evict stale entry
public UserProfileResponse updateProfile(UUID userId, ...) { ... }
```

### Redis key format

```
user-profiles::c89c5805-0106-4525-89d8-3942960166b3
user-profiles::email:john@example.com
```

### Serialization

Values are stored as **JSON** (not Java serialization) using `GenericJackson2JsonRedisSerializer`. This means:
- Human-readable in Redis CLI
- Cache survives application restarts (no class compatibility issues)
- `JavaTimeModule` registered to handle `Instant` fields

### TTL: 10 minutes

Configured in `CacheConfig.java`:
```java
defaultConfig.entryTtl(Duration.ofMinutes(10))
```

After 10 minutes, the next read hits PostgreSQL and refreshes the cache.

---

## Feign Client & Fallback

`UserServiceFeignClient` is the interface **other services use** to call user-service internally:

```java
@FeignClient(name = "user-service", path = "/users/v1",
             fallbackFactory = UserServiceFeignClientFallbackFactory.class)
public interface UserServiceFeignClient {
    @GetMapping("/internal/{userId}")
    UserProfileResponse getUserProfile(@PathVariable UUID userId);
}
```

The `/internal/{userId}` endpoint bypasses JWT authentication (internal network only).

### Fallback behavior

When user-service is down or circuit breaker opens:
```java
return new UserProfileResponse(
    userId, "unknown@unknown.com",
    "Unknown", "User", "Unknown User",
    null, null, "STUDENT", "ACTIVE", null
);
```
Non-critical callers (e.g., displaying instructor name on a course) get a graceful degraded response instead of a 500 error.

---

## Database Schema

### Flyway Migrations

| Version | Description |
|---------|-------------|
| V1 | `user_profiles` table with constraints and indexes |

### user_profiles table

```sql
CREATE TABLE user_profiles (
    user_id     UUID         PRIMARY KEY,          -- from auth-service (not generated here)
    email       VARCHAR(255) UNIQUE NOT NULL,
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(50)  NOT NULL,
    bio         VARCHAR(500),
    avatar_url  VARCHAR(2048),
    role        VARCHAR(20)  NOT NULL,             -- ADMIN | INSTRUCTOR | STUDENT
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | DELETED
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_role   CHECK (role IN ('ADMIN', 'INSTRUCTOR', 'STUDENT')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

-- Indexes for common query patterns
CREATE INDEX idx_user_profiles_email  ON user_profiles (email);
CREATE INDEX idx_user_profiles_role   ON user_profiles (role);
CREATE INDEX idx_user_profiles_status ON user_profiles (status);
```

**Design note:** `user_id` is **not** auto-generated here — it comes from auth-service. This is the shared key that ties both services together without a foreign key constraint across service boundaries.

---

## Observability

### Metrics (Prometheus)
```promql
# Profile cache hit rate
rate(cache_gets_total{name="user-profiles",result="hit"}[5m])
/ rate(cache_gets_total{name="user-profiles"}[5m])

# Profile update rate
rate(http_server_requests_seconds_count{uri="/users/v1/me",method="PUT"}[5m])

# Consumer lag (messages waiting in queue)
# Check in RabbitMQ dashboard: lms.user.user-registered → Ready count
```

### Logs (Loki)
```logql
{application="user-service"} |= "User profile created"
{application="user-service"} |= "Message sent to DLQ"
{application="user-service"} |= "idempotent"
```

### RabbitMQ Dashboard
- **Queue:** `lms.user.user-registered` — Ready should be 0 when consumer is up
- **DLQ:** `lms.user.dlq` — Should always be 0 in healthy operation

---

## Running Locally

### Docker (recommended)
```bash
# All services must be up first
cd infrastructure/docker-compose
docker compose --profile infra up -d
docker compose --profile platform up -d
docker compose --profile services up -d

# Register a user (creates profile automatically via event)
curl -X POST http://localhost:8080/auth/v1/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com","password":"Password123!","role":"STUDENT"}'

# Login to get token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"Password123!"}' | python -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# Get my profile
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/users/v1/me
```

### IntelliJ (dev profile)
1. Start infra: `docker compose --profile infra up -d`
2. Run `UserServiceApplication` with `-Dspring.profiles.active=dev`
3. Service available at `http://localhost:8081` (direct, no gateway)

### Rebuild after changes
```bash
mvn clean package -DskipTests -pl :user-service
docker compose build user-service
docker compose --profile services up -d user-service
```
