# Auth Service

The authentication and authorization microservice for the LMS Platform. Responsible for user registration, JWT issuance, token refresh, logout, and publishing user lifecycle events to downstream services.

- **Port:** `9000`
- **Database:** PostgreSQL (`lms_auth`)
- **Messaging:** RabbitMQ producer (transactional outbox)
- **Access:** Via Gateway only (`http://localhost:8080/auth/v1/...`)

---

## Table of Contents

1. [API Endpoints](#api-endpoints)
2. [Architecture](#architecture)
3. [Authentication System — JWT & RS256](#authentication-system)
4. [JWKS Endpoint & Gateway Validation](#jwks-endpoint--gateway-validation)
5. [Refresh Token Strategy](#refresh-token-strategy)
6. [Account Security](#account-security)
7. [Event Publishing — Transactional Outbox](#event-publishing--transactional-outbox)
8. [Database Schema](#database-schema)
9. [Observability](#observability)
10. [Running Locally](#running-locally)

---

## API Endpoints

All routes are prefixed `/auth/v1` and accessed through the Gateway on port `8080`.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/auth/v1/register` | Public | Register a new user |
| `POST` | `/auth/v1/login` | Public | Login, receive JWT + refresh cookie |
| `POST` | `/auth/v1/refresh` | Cookie | Rotate refresh token, get new JWT |
| `POST` | `/auth/v1/logout` | Public* | Revoke refresh token, clear cookie |
| `GET`  | `/auth/v1/.well-known/jwks.json` | Public | RSA public key for JWT verification |

> *Logout is `permitAll` by design — even an expired access token should not block logout. Security is enforced via the HttpOnly refresh token cookie.

### Register

```http
POST /auth/v1/register
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "password": "MyPassword123!",
  "role": "STUDENT"
}
```

**Roles:** `STUDENT` | `INSTRUCTOR` | `ADMIN`

**Response `201 Created`:**
```json
{
  "userId": "c89c5805-0106-4525-89d8-3942960166b3",
  "email": "john@example.com",
  "role": "STUDENT",
  "message": "Registration successful. Please verify your email."
}
```

### Login

```http
POST /auth/v1/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "MyPassword123!"
}
```

**Response `200 OK`:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": "c89c5805-...",
  "email": "john@example.com",
  "role": "STUDENT"
}
```

Also sets an HttpOnly cookie:
```
Set-Cookie: lms-refresh-token=<raw-token>; HttpOnly; Secure; SameSite=Strict; Path=/auth/v1/refresh; Max-Age=604800
```

### Refresh Token

```http
POST /auth/v1/refresh
Cookie: lms-refresh-token=<raw-token>
```

Returns a new `accessToken` and rotates the refresh token cookie. The old token is immediately revoked.

### Logout

```http
POST /auth/v1/logout
Cookie: lms-refresh-token=<raw-token>
```

Revokes the refresh token in the database and clears the cookie. No Bearer token needed.

---

## Architecture

The service follows **Hexagonal Architecture** (Ports & Adapters). The domain has zero Spring or JPA annotations.

```
com.lms.authservice
├── api/
│   └── controller/
│       ├── AuthController.java          ← REST endpoints
│       └── GlobalExceptionHandler.java  ← @RestControllerAdvice
│
├── application/
│   ├── dto/request/                     ← LoginRequest, RegisterRequest, ...
│   ├── dto/response/                    ← AuthResponse, LoginResult, RegisterResponse
│   ├── port/                            ← Interface contracts
│   │   ├── TokenService.java
│   │   ├── PasswordEncoder.java
│   │   └── EventPublisher.java
│   └── service/
│       └── AuthApplicationService.java  ← Use-case orchestration
│
├── domain/
│   ├── model/
│   │   ├── User.java                    ← Aggregate root (business rules here)
│   │   ├── RefreshToken.java            ← Token revocation logic
│   │   ├── OutboxEvent.java             ← Outbox envelope
│   │   └── Role.java
│   ├── repository/                      ← Port interfaces (owned by domain)
│   │   ├── UserRepository.java
│   │   ├── RefreshTokenRepository.java
│   │   └── OutboxRepository.java
│   └── event/
│       └── UserRegisteredEvent.java     ← Domain event record
│
└── infrastructure/
    ├── persistence/
    │   ├── entity/                      ← @Entity classes (JPA, separate from domain)
    │   ├── mapper/                      ← MapStruct mappers (entity ↔ domain)
    │   ├── adapter/                     ← Implements repository ports
    │   └── repository/                  ← Spring Data JPA interfaces
    ├── messaging/
    │   ├── RabbitMQConfig.java          ← Exchange, queue, converter beans
    │   ├── RabbitMQEventPublisher.java  ← Implements EventPublisher port
    │   └── outbox/
    │       └── OutboxPublisher.java     ← @Scheduled, polls outbox → RabbitMQ
    ├── scheduler/
    │   └── TokenCleanupScheduler.java   ← Deletes expired refresh tokens daily
    └── security/
        ├── config/SecurityConfig.java   ← Spring Security filter chain
        └── jwt/
            ├── JwtTokenService.java     ← Implements TokenService port (JJWT)
            ├── JwksController.java      ← Serves RSA public key as JWKS
            └── RsaKeyProperties.java    ← Config properties for RSA key pair
```

---

## Authentication System

### Why RSA (RS256) instead of HMAC (HS256)?

| | RS256 (this system) | HS256 |
|--|--|--|
| Keys | Private key signs · Public key verifies | Same secret for both |
| Secret sharing | Private key never leaves auth-service | Every verifier needs the secret |
| Compromise | Compromised service exposes only public key | Compromised service exposes signing secret |
| Key rotation | Serve old + new key in JWKS during transition | All consumers must update simultaneously |

With RS256, the Gateway and any future service can verify tokens using **only the public key**. The private key never leaves `auth-service`.

### JWT Structure

Every access token contains:

```
Header:  { "alg": "RS256", "typ": "JWT" }

Payload: {
  "jti": "uuid",               ← unique token ID
  "sub": "user-uuid",          ← user's UUID (used as principal)
  "iss": "lms-platform",       ← issuer
  "iat": 1710000000,           ← issued at (Unix seconds)
  "exp": 1710000900,           ← expires at (iat + 15 minutes)
  "email": "john@example.com",
  "role": "STUDENT",
  "firstName": "John",
  "lastName": "Doe"
}

Signature: RSA_sign(base64url(header) + "." + base64url(payload), privateKey)
```

**Expiry:** Access tokens expire after **15 minutes**. Refresh tokens expire after **7 days**.

### Complete Login Data Flow

```
Client                  Gateway (8080)          Auth-Service (9000)        PostgreSQL
  │                         │                          │                       │
  │── POST /auth/v1/login ──►│                          │                       │
  │                         │── forward (no JWT check ──►│                      │
  │                         │   for /auth paths)         │                      │
  │                         │                          SELECT user by email ───►│
  │                         │                          │◄─────────────────── row│
  │                         │                     BCrypt.matches(raw, hash)     │
  │                         │                          │                        │
  │                         │                     RSA sign JWT (private key)    │
  │                         │                          │                        │
  │                         │                     SHA-256 hash refresh token    │
  │                         │                     INSERT refresh_tokens ───────►│
  │◄── 200 + JWT + Cookie ──│◄── AuthResponse ─────────│                        │
```

### Complete Registration Data Flow

```
Client               Gateway             Auth-Service           PostgreSQL     RabbitMQ
  │                     │                    │                      │              │
  │─ POST /register ───►│                    │                      │              │
  │                     │──── forward ──────►│                      │              │
  │                     │              BCrypt password              │              │
  │                     │              User.create()                │              │
  │                     │              INSERT users ───────────────►│              │
  │                     │              INSERT outbox_events ────────►│              │
  │                     │              ↑ SAME TRANSACTION ↑         │              │
  │◄─── 201 Created ────│◄─── 201 ─────│                            │              │
  │                     │              │                            │              │
  │                     │    (5 seconds later — @Scheduled)         │              │
  │                     │              OutboxPublisher.publishEvents()            │
  │                     │              SELECT pending outbox ────────►│            │
  │                     │              send(raw JSON bytes) ──────────────────────►│
  │                     │              UPDATE outbox PROCESSED ──────►│            │
  │                     │                                             │    user-service consumes
  │                     │                                             │    notification-service consumes
```

---

## JWKS Endpoint & Gateway Validation

### What the JWKS endpoint returns

```
GET /auth/v1/.well-known/jwks.json

{
  "keys": [{
    "kty": "RSA",
    "use": "sig",
    "alg": "RS256",
    "kid": "abc123def456ghi7",   ← SHA-256 of public key (first 16 chars)
    "n":  "0vx7agoebGcQSuu...", ← RSA modulus (Base64URL)
    "e":  "AQAB"                 ← RSA public exponent 65537 (Base64URL)
  }]
}
```

The RSA public key is mathematically defined by two numbers: **modulus `n`** and **exponent `e`**. Given these two, anyone can verify a signature created by the matching private key — without knowing the private key itself.

### How Spring Cloud Gateway validates tokens internally

**Step 1 — Gateway startup:**
```yaml
spring.security.oauth2.resourceserver.jwt.jwk-set-uri:
  http://auth-service:9000/auth/v1/.well-known/jwks.json
```
Spring creates a `NimbusReactiveJwtDecoder`. On startup it fetches the JWKS URI and caches the public key in a `JwkSet`.

**Step 2 — Per-request validation:**
```
Request: Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6ImFiYzEyMyJ9...

1. BearerTokenAuthenticationFilter extracts the token
2. NimbusReactiveJwtDecoder.decode(token)
   a. Base64URL-decode the header → get "alg":"RS256", "kid":"abc123"
   b. Look up the key with kid="abc123" in cached JwkSet
   c. Reconstruct RSAPublicKey from (n, e)
   d. Verify: RSA_verify(signature, header+payload, publicKey) → true/false
   e. Check exp > now, iss == "lms-platform"
3. Valid → JwtAuthenticationToken placed in ReactiveSecurityContextHolder
4. Invalid → 401 Unauthorized (no downstream call)
5. Gateway forwards request to lb://auth-service via Eureka
```

**Key rotation:** If a request arrives with an unknown `kid`, the gateway automatically re-fetches the JWKS endpoint and updates its cache. This allows zero-downtime key rotation by serving both old and new key in the JWKS response during a transition window.

**Gateway SecurityConfig:**
```java
// gatewayserver SecurityConfig.java
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> {})  // auto-wires NimbusReactiveJwtDecoder from JWKS URI
)
```

---

## Refresh Token Strategy

### Storage
The raw refresh token **is never stored**. Only its SHA-256 hash is persisted:

```java
// AuthApplicationService.java
String rawRefreshToken = tokenService.generateRefreshToken(); // 64 random bytes
String hash = sha256(rawRefreshToken);
refreshTokenRepository.save(RefreshToken.create(userId, hash, expiresAt));
// raw token → HttpOnly cookie only
// hash → database only
```

If the database is breached, attackers get hashes they cannot reverse.

### Token Rotation
Every `/refresh` call:
1. Validates the incoming token hash exists and is not revoked/expired
2. **Immediately revokes** the old token (`revoked = true`)
3. Issues a new token and saves its hash

### Reuse Detection
```java
// If a revoked token is presented again:
if (refreshToken.isRevoked()) {
    // Possible token theft — nuke all sessions for this user
    refreshTokenRepository.revokeAllByUserId(refreshToken.getUserId());
    throw new InvalidTokenException("Refresh token is expired or revoked");
}
```
Presenting a revoked token triggers revocation of **all** active sessions for that user, forcing re-login on every device.

---

## Account Security

### Account Locking
Defined in `User.java` (domain model — no Spring dependency):

```
5 failed login attempts → account locked for 15 minutes
Lock auto-expires: isAccountLocked() checks Instant.now() vs lockedUntil
Successful login resets the counter
```

### Password Hashing
BCrypt with cost factor **12** (configured in `SecurityConfig.java`):
```java
new BCryptPasswordEncoder(12)
```
BCrypt is adaptive — cost factor can be increased as hardware gets faster without changing stored hashes.

---

## Event Publishing — Transactional Outbox

### The Problem
Naive approach:
```
1. INSERT user into PostgreSQL   ─┐ Two separate operations
2. publish to RabbitMQ           ─┘ Server crash between them = lost event
```

### The Solution — Outbox Pattern
```
1. INSERT user            ─┐ Single ACID transaction
2. INSERT outbox_event    ─┘ Either both commit or both roll back

3. @Scheduled every 5s: read pending outbox rows → publish to RabbitMQ → mark processed
```

Even if the server crashes after step 2, the outbox row is still in the database. The scheduler picks it up on restart and publishes.

### Outbox table schema
```sql
CREATE TABLE outbox_events (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100),     -- "User"
    aggregate_id   UUID,             -- userId
    event_type     VARCHAR(100),     -- "UserRegisteredEvent"
    payload        JSONB NOT NULL,   -- serialized event JSON
    status         VARCHAR(20)  DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    retry_count    INTEGER      DEFAULT 0,
    created_at     TIMESTAMPTZ  DEFAULT NOW(),
    processed_at   TIMESTAMPTZ
);
```

### Publishing — Why raw bytes matter
```java
// OutboxPublisher.java — CORRECT approach
Message message = MessageBuilder
    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
    .andProperties(new MessageProperties())
    .build();
message.getMessageProperties().setContentType("application/json");
rabbitTemplate.send(AUTH_EXCHANGE, ROUTING_KEY, message);

// WRONG — causes double serialization:
// rabbitTemplate.convertAndSend(exchange, key, event.getPayload());
// Jackson2JsonMessageConverter wraps the String in another JSON string
// Consumer receives: "\"{'role':'STUDENT',...}\"" instead of {"role":"STUDENT",...}
```

---

## Database Schema

### Flyway Migrations

| Version | Description |
|---------|-------------|
| V1 | `users` table |
| V2 | `refresh_tokens` table |
| V3 | Seed admin user |
| V4 | `outbox_events` table |
| V5 | Add device tracking columns to `refresh_tokens` |

### users table
```sql
id                UUID PRIMARY KEY
email             VARCHAR(255) UNIQUE NOT NULL
password_hash     VARCHAR(255) NOT NULL        -- BCrypt hash
first_name        VARCHAR(100)
last_name         VARCHAR(100)
role              VARCHAR(20)                  -- STUDENT | INSTRUCTOR | ADMIN
enabled           BOOLEAN DEFAULT true
email_verified    BOOLEAN DEFAULT false
failed_login_attempts INTEGER DEFAULT 0
locked_until      TIMESTAMPTZ                  -- null = not locked
created_at        TIMESTAMPTZ
updated_at        TIMESTAMPTZ
```

### refresh_tokens table
```sql
id           UUID PRIMARY KEY
user_id      UUID NOT NULL
token_hash   VARCHAR(255) UNIQUE NOT NULL   -- SHA-256 of raw token
expires_at   TIMESTAMPTZ NOT NULL
created_at   TIMESTAMPTZ NOT NULL
revoked      BOOLEAN DEFAULT false
revoked_at   TIMESTAMPTZ
device_name  VARCHAR(255)                   -- V5: optional device tracking
ip_address   VARCHAR(45)
user_agent   TEXT
last_used_at TIMESTAMPTZ
```

---

## Observability

### Custom Metrics (Micrometer → Prometheus)

| Metric | Type | Description |
|--------|------|-------------|
| `auth.registrations.total` | Counter | Total successful registrations |
| `auth.login.success.total` | Counter | Total successful logins |
| `auth.login.failure.total` | Counter | Total failed login attempts |

**Prometheus queries:**
```promql
# Login success rate (last 5 min)
rate(auth_login_success_total[5m])

# Failed login rate (potential brute force)
rate(auth_login_failure_total[5m])

# Registration growth
increase(auth_registrations_total[1h])
```

### Logs (Loki)
All logs include `[service, traceId, spanId]` for correlation:
```
[auth-service,3c792a94af93,ec8497a7] Login attempt for email=john@example.com
[auth-service,3c792a94af93,ec8497a7] Login successful userId=c89c5805
```

**Useful Loki queries:**
```logql
{application="auth-service"} |= "Failed login attempt"
{application="auth-service"} |= "SECURITY: Revoked refresh token reused"
{application="auth-service"} | logfmt | level="ERROR"
```

### Traces (OpenTelemetry → Tempo)
Each request creates a distributed trace. Find traces in Grafana → Explore → Tempo using the `traceId` from any log line.

---

## Running Locally

### Option 1: Docker (recommended)
```bash
# Start everything
cd infrastructure/docker-compose
docker compose --profile infra up -d
docker compose --profile platform up -d
docker compose --profile services up -d

# Test register
curl -X POST http://localhost:8080/auth/v1/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com","password":"Password123!","role":"STUDENT"}'

# Test login
curl -X POST http://localhost:8080/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"Password123!"}'
```

### Option 2: IntelliJ (dev profile)
1. Start infra: `docker compose --profile infra up -d`
2. Run `AuthServiceApplication` with VM option: `-Dspring.profiles.active=dev`
3. Service starts on `http://localhost:9000` (direct, no gateway)

### Rebuild after code changes
```bash
mvn clean package -DskipTests -pl :auth-service
docker compose build auth-service
docker compose --profile services up -d auth-service
```

### Useful dashboards
| Tool | URL | Credentials |
|------|-----|-------------|
| RabbitMQ | http://localhost:15672 | `lms_user` / `lms_password` |
| Grafana | http://localhost:3000 | `admin` / `lms_grafana_secret` |
| Prometheus | http://localhost:9090 | — |
| MailHog | http://localhost:8025 | — |
| Eureka | http://localhost:8761 | `eureka-admin` / `eureka-secret` |
