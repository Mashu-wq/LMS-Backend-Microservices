# Course Service

**Port:** `8082` | **Database:** MongoDB (`lms_courses`) | **Pattern:** Hexagonal Architecture

The course service is the content backbone of the LMS platform. It owns the full lifecycle of a course — from creation and content structuring (sections and lessons) through publishing and student enrollment. It is the only service that writes to the `lms_courses` MongoDB database.

---

## Table of Contents

1. [Responsibility Boundary](#1-responsibility-boundary)
2. [Architecture Overview](#2-architecture-overview)
3. [Domain Model](#3-domain-model)
4. [Package Structure](#4-package-structure)
5. [API Reference](#5-api-reference)
6. [Security Model](#6-security-model)
7. [Persistence Layer](#7-persistence-layer)
8. [Caching Strategy](#8-caching-strategy)
9. [Messaging — Events Published](#9-messaging--events-published)
10. [Messaging — Events Consumed](#10-messaging--events-consumed)
11. [Course Lifecycle State Machine](#11-course-lifecycle-state-machine)
12. [Enrollment Flow](#12-enrollment-flow)
13. [Inter-Service Communication](#13-inter-service-communication)
14. [Observability](#14-observability)
15. [Configuration](#15-configuration)
16. [Key Design Decisions](#16-key-design-decisions)
17. [Error Handling](#17-error-handling)

---

## 1. Responsibility Boundary

| Owns | Does NOT Own |
|------|--------------|
| Course CRUD (create, read, update) | User profiles or instructor identity |
| Section and lesson management | Payment processing |
| Course status lifecycle (DRAFT → PUBLISHED → ARCHIVED) | Progress tracking (lesson completion) |
| Student enrollment records | Search indexing (delegates via event) |
| Enrollment counter on course document | Email notifications (delegates via event) |
| Publishing `CoursePublishedEvent`, `StudentEnrolledEvent` | Auth / JWT issuance |

---

## 2. Architecture Overview

The service follows **Hexagonal Architecture** (Ports and Adapters). The domain has zero dependency on Spring or MongoDB. All framework coupling is isolated in the `infrastructure` layer.

```
                          ┌─────────────────────────────────────────────┐
                          │              course-service                  │
                          │                                             │
  HTTP Request ──────────►│  api/                                       │
  (via Gateway)           │  ├── controller/CourseController            │
                          │  └── exception/GlobalExceptionHandler        │
                          │            │                                 │
                          │            ▼                                 │
                          │  application/                               │
                          │  ├── service/CourseService      ◄────────── │── Redis Cache
                          │  ├── service/EnrollmentService              │
                          │  ├── dto/request/  (API contracts)          │
                          │  ├── dto/response/ (API contracts)          │
                          │  └── port/CourseEventPublisher (interface)  │
                          │            │                                 │
                          │            ▼                                 │
                          │  domain/                                    │
                          │  ├── model/Course          (aggregate root) │
                          │  ├── model/Section         (value object)   │
                          │  ├── model/Lesson          (value object)   │
                          │  ├── model/Enrollment      (aggregate)      │
                          │  ├── model/CourseStatus    (state machine)  │
                          │  ├── model/LessonType      (enum)           │
                          │  ├── repository/CourseRepository (port)     │
                          │  └── repository/EnrollmentRepository (port) │
                          │            │                                 │
                          │            ▼                                 │
                          │  infrastructure/                            │
                          │  ├── persistence/                           │──► MongoDB
                          │  │   ├── document/CourseDocument            │    lms_courses
                          │  │   ├── document/EnrollmentDocument        │
                          │  │   ├── mapper/CoursePersistenceMapper     │
                          │  │   ├── adapter/CourseRepositoryAdapter    │
                          │  │   └── repository/MongoCourseCrudRepo     │
                          │  ├── messaging/                             │──► RabbitMQ
                          │  │   ├── CourseEventPublisherImpl           │    lms.course.events
                          │  │   ├── PaymentCompletedEventConsumer      │◄── lms.payment.events
                          │  │   └── RabbitMQConfig                    │
                          │  ├── cache/CacheConfig                      │──► Redis
                          │  ├── security/SecurityConfig                │
                          │  └── index/MongoIndexInitializer            │
                          └─────────────────────────────────────────────┘
```

---

## 3. Domain Model

### 3.1 Aggregate Hierarchy

The domain models a course as a single **aggregate root** containing a hierarchy of embedded value objects. Everything is stored as one MongoDB document for atomicity.

```
Course  (Aggregate Root)
│
│   courseId        : String (UUID)       ← immutable
│   title           : String
│   description     : String
│   shortDescription: String
│   instructorId    : UUID                ← immutable
│   instructorName  : String              ← denormalized for read perf
│   status          : CourseStatus        ← DRAFT | PUBLISHED | ARCHIVED
│   category        : String
│   language        : String
│   level           : String             ← BEGINNER | INTERMEDIATE | ADVANCED
│   price           : BigDecimal
│   thumbnailUrl    : String
│   tags            : List<String>
│   totalEnrollments: int                 ← counter, incremented on enrollment
│   averageRating   : double
│   ratingCount     : int
│   createdAt       : Instant
│   updatedAt       : Instant
│   publishedAt     : Instant
│
└── sections : List<Section>   (value objects, ordered)
    │
    │   sectionId  : String (UUID)
    │   title      : String
    │   description: String
    │   orderIndex : int
    │
    └── lessons : List<Lesson>   (value objects, ordered)

            lessonId       : String (UUID)
            title          : String
            description    : String
            contentUrl     : String    ← S3 video URL or article ID
            lessonType     : LessonType (VIDEO | ARTICLE | QUIZ | ASSIGNMENT)
            durationMinutes: int
            orderIndex     : int
            previewEnabled : boolean   ← free preview before enrollment
```

**Why embed sections and lessons in one document?**
- A course's sections and lessons are always read together (you never fetch just a lesson in isolation)
- The cardinality is bounded — a course won't have 10,000 lessons
- One MongoDB document = one atomic write, no distributed transaction needed

### 3.2 Enrollment (separate aggregate)

```
Enrollment
│
│   enrollmentId : String (UUID)
│   courseId     : String           ← foreign key (not embedded)
│   studentId    : UUID
│   courseTitle  : String           ← denormalized to avoid join on listing
│   instructorId : UUID             ← denormalized for analytics
│   status       : EnrollmentStatus ← ACTIVE | COMPLETED | CANCELLED | REFUNDED
│   enrolledAt   : Instant
│   completedAt  : Instant
│   cancelledAt  : Instant
```

**Why is Enrollment a separate collection?** A course can have thousands of enrollments. Embedding them in the Course document would make it grow without bound and violate MongoDB's 16 MB document limit.

### 3.3 Domain Invariants

These rules are enforced inside the domain objects themselves, not in the service layer:

| Invariant | Where Enforced |
|-----------|----------------|
| Course title cannot be blank or exceed 200 chars | `Course.validateTitle()` |
| Cannot add sections/lessons to a PUBLISHED or ARCHIVED course | `Course.requireDraft()` |
| Cannot publish without ≥1 section and ≥1 lesson | `Course.publish()` |
| Status transitions are validated against state machine | `CourseStatus.canTransitionTo()` |
| Cannot enroll in an unpublished course | `EnrollmentService.enroll()` |
| Enrollment is idempotent — duplicate enrollments are rejected | `EnrollmentService.enroll()` |

---

## 4. Package Structure

```
com.lms.courseservice
│
├── api/
│   ├── controller/
│   │   └── CourseController.java         ← All REST endpoints
│   └── exception/
│       └── GlobalExceptionHandler.java   ← @RestControllerAdvice, RFC 9457 Problem Details
│
├── application/
│   ├── dto/
│   │   ├── request/
│   │   │   ├── CreateCourseRequest.java
│   │   │   ├── UpdateCourseRequest.java
│   │   │   ├── AddSectionRequest.java
│   │   │   └── AddLessonRequest.java
│   │   └── response/
│   │       ├── CourseResponse.java        ← full detail (with sections/lessons)
│   │       ├── CourseSummaryResponse.java ← lightweight (catalog listing)
│   │       ├── SectionResponse.java
│   │       ├── LessonResponse.java
│   │       └── EnrollmentResponse.java
│   ├── port/
│   │   └── CourseEventPublisher.java      ← interface (dependency inversion)
│   └── service/
│       ├── CourseService.java             ← course use cases + Redis cache
│       └── EnrollmentService.java         ← enrollment use cases
│
├── domain/
│   ├── model/
│   │   ├── Course.java                   ← aggregate root
│   │   ├── Section.java                  ← value object
│   │   ├── Lesson.java                   ← value object
│   │   ├── Enrollment.java               ← separate aggregate
│   │   ├── CourseStatus.java             ← state machine enum
│   │   └── LessonType.java               ← VIDEO | ARTICLE | QUIZ | ASSIGNMENT
│   ├── repository/
│   │   ├── CourseRepository.java         ← port (domain-owned interface)
│   │   └── EnrollmentRepository.java     ← port (domain-owned interface)
│   └── exception/
│       ├── CourseNotFoundException.java
│       ├── CourseOperationException.java
│       ├── EnrollmentException.java
│       └── UnauthorizedCourseAccessException.java
│
└── infrastructure/
    ├── persistence/
    │   ├── document/
    │   │   ├── CourseDocument.java        ← @Document(collection="courses")
    │   │   ├── SectionDocument.java
    │   │   ├── LessonDocument.java
    │   │   └── EnrollmentDocument.java   ← @Document(collection="enrollments")
    │   ├── mapper/
    │   │   ├── CoursePersistenceMapper.java     ← hand-written (no MapStruct)
    │   │   └── EnrollmentPersistenceMapper.java
    │   ├── adapter/
    │   │   ├── CourseRepositoryAdapter.java     ← implements domain port
    │   │   └── EnrollmentRepositoryAdapter.java
    │   └── repository/
    │       ├── MongoCourseCrudRepository.java   ← Spring Data MongoRepository
    │       └── MongoEnrollmentCrudRepository.java
    ├── messaging/
    │   ├── RabbitMQConfig.java                 ← exchange, queue, binding declarations
    │   ├── CourseEventPublisherImpl.java        ← implements CourseEventPublisher port
    │   └── PaymentCompletedEventConsumer.java  ← @RabbitListener, manual ACK
    ├── cache/
    │   └── CacheConfig.java                    ← RedisCacheManager, 5-min TTL
    ├── security/
    │   └── SecurityConfig.java                 ← JWT resource server, RBAC
    └── index/
        └── MongoIndexInitializer.java          ← creates indexes on startup
```

**Why hand-written mappers instead of MapStruct?**
The domain models use private constructors and static factory methods (`Course.create()`, `Course.reconstitute()`). MapStruct requires public constructors or builders annotated with `@Builder`. Using hand-written mappers avoids polluting the domain model with framework annotations, keeping the domain pure.

---

## 5. API Reference

Base path: `/courses/v1`

### 5.1 Public Endpoints (no auth required)

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/courses/v1` | Paginated published course catalog | `Page<CourseSummaryResponse>` |
| `GET` | `/courses/v1/{courseId}` | Get a single course (full detail) | `CourseResponse` |

**Query parameters for catalog:**
- `category` (optional) — filter by category string
- `page` (default: `0`), `size` (default: `20`, max: `50`)
- `sortBy` (default: `publishedAt`), `direction` (default: `DESC`)

### 5.2 Instructor Endpoints (role: `INSTRUCTOR` or `ADMIN`)

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/courses/v1` | Create a new course (status: DRAFT) | `201 CourseResponse` |
| `PUT` | `/courses/v1/{courseId}` | Update course metadata | `CourseResponse` |
| `POST` | `/courses/v1/{courseId}/sections` | Add a section (requires DRAFT status) | `201 CourseResponse` |
| `POST` | `/courses/v1/{courseId}/sections/{sectionId}/lessons` | Add a lesson | `201 CourseResponse` |
| `DELETE` | `/courses/v1/{courseId}/sections/{sectionId}/lessons/{lessonId}` | Remove a lesson | `CourseResponse` |
| `POST` | `/courses/v1/{courseId}/publish` | Publish course (requires ≥1 section and ≥1 lesson) | `CourseResponse` |
| `POST` | `/courses/v1/{courseId}/archive` | Archive course | `CourseResponse` |
| `GET` | `/courses/v1/instructor/my-courses` | Get instructor's own courses (all statuses) | `Page<CourseSummaryResponse>` |
| `GET` | `/courses/v1/{courseId}/enrollments` | Get enrollment list for owned course | `Page<EnrollmentResponse>` |

### 5.3 Student Endpoints (role: `STUDENT`)

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/courses/v1/{courseId}/enroll` | Enroll in a published course | `201 EnrollmentResponse` |
| `GET` | `/courses/v1/my-enrollments` | Get own enrollment list | `Page<EnrollmentResponse>` |

### 5.4 Internal Endpoints (service-to-service, no auth)

| Method | Path | Called By | Description |
|--------|------|-----------|-------------|
| `GET` | `/courses/v1/internal/{courseId}` | progress-service, payment-service | Fetch course detail |
| `GET` | `/courses/v1/internal/student/{studentId}/enrolled-courses` | progress-service | Get enrolled course IDs |
| `GET` | `/courses/v1/internal/{courseId}/is-enrolled/{studentId}` | progress-service | Check enrollment |

### 5.5 Request / Response Shapes

**CreateCourseRequest:**
```json
{
  "title": "Spring Boot Microservices",
  "description": "Full description...",
  "shortDescription": "Learn Spring Boot",
  "category": "Programming",
  "language": "English",
  "level": "BEGINNER",
  "price": 49.99,
  "thumbnailUrl": "https://...",
  "tags": ["java", "spring", "microservices"]
}
```

**CourseResponse (full):**
```json
{
  "courseId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Spring Boot Microservices",
  "description": "...",
  "shortDescription": "...",
  "instructorId": "...",
  "instructorName": "Jane Doe",
  "status": "PUBLISHED",
  "category": "Programming",
  "language": "English",
  "level": "BEGINNER",
  "price": 49.99,
  "thumbnailUrl": "...",
  "tags": ["java", "spring"],
  "sections": [
    {
      "sectionId": "...",
      "title": "Introduction",
      "orderIndex": 1,
      "lessons": [
        {
          "lessonId": "...",
          "title": "Welcome",
          "lessonType": "VIDEO",
          "durationMinutes": 5,
          "orderIndex": 1,
          "previewEnabled": true
        }
      ]
    }
  ],
  "totalEnrollments": 142,
  "averageRating": 4.7,
  "totalLessons": 24,
  "totalDurationMinutes": 320,
  "createdAt": "2026-01-15T10:00:00Z",
  "updatedAt": "2026-02-01T12:00:00Z",
  "publishedAt": "2026-02-01T12:00:00Z"
}
```

---

## 6. Security Model

### 6.1 JWT Validation

The service is a Spring Security **OAuth2 Resource Server**. It validates incoming JWTs by fetching the RSA public key from the auth-service JWKS endpoint at startup and on key rotation:

```
jwk-set-uri: http://auth-service:9000/auth/v1/.well-known/jwks.json
```

The JWT is issued by `auth-service` (RS256, 2048-bit RSA). The course-service never sees the private key.

### 6.2 Role Extraction

The JWT contains a `role` claim (`"INSTRUCTOR"`, `"ADMIN"`, or `"STUDENT"`). A custom `JwtAuthenticationConverter` maps this to a Spring `GrantedAuthority`:

```java
jwt.getClaimAsString("role") → "INSTRUCTOR"
            ↓
SimpleGrantedAuthority("ROLE_INSTRUCTOR")
```

### 6.3 Authorization Rules

| Endpoint pattern | Rule |
|-----------------|------|
| `GET /courses/v1/**` | Public — no token required |
| `POST /courses/v1` and instructor operations | `@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")` |
| `POST /courses/v1/{id}/enroll` | `@PreAuthorize("hasRole('STUDENT')")` |
| `GET /courses/v1/my-enrollments` | `@PreAuthorize("hasRole('STUDENT')")` |
| `/courses/v1/internal/**` | `permitAll()` — internal network only |

### 6.4 Ownership Check

Beyond role-based access, the service enforces **data ownership**. An instructor can only modify their own courses. This is checked in the application service:

```java
// CourseService.loadAndAuthorize()
boolean isAdmin = "ADMIN".equals(role);
if (!isAdmin && !course.isOwnedBy(requestingUserId)) {
    throw new UnauthorizedCourseAccessException(...);
}
```

The `instructorId` extracted from the JWT `sub` claim is compared against the `course.instructorId` field stored in MongoDB.

---

## 7. Persistence Layer

### 7.1 MongoDB Collections

| Collection | Document | Purpose |
|------------|----------|---------|
| `courses` | `CourseDocument` | Entire course hierarchy (sections + lessons embedded) |
| `enrollments` | `EnrollmentDocument` | Student-course relationships (separate collection) |

### 7.2 Why MongoDB over PostgreSQL

- Course content is a **document hierarchy** (course → sections → lessons) that maps naturally to a nested document
- No cross-collection joins are needed — a course is always read as a whole
- Schema flexibility allows adding new lesson types or course metadata without migrations
- The entire course hierarchy can be saved/loaded in one atomic operation

### 7.3 Mapping Strategy

Spring Data MongoDB cannot auto-map domain objects that use private constructors and `reconstitute()` factory methods. A hand-written `CoursePersistenceMapper` handles the conversion:

```
Domain Object            MongoDB Document
─────────────────        ─────────────────────────
Course.create()      →   CourseDocument (saved via MongoRepository)
Course.reconstitute()←   CourseDocument (loaded from MongoDB)
```

The mapper is the only place where domain ↔ infrastructure conversion happens. The domain model has zero `@Document`, `@Indexed`, or Spring annotations.

### 7.4 Index Strategy

`auto-index-creation: false` — indexes are created programmatically by `MongoIndexInitializer` on `ApplicationReadyEvent`. This avoids the risk of index creation blocking writes on a large collection in production.

**Indexes on `courses` collection:**

| Index name | Fields | Purpose |
|-----------|--------|---------|
| `idx_status_publishedAt` | `status ASC, publishedAt DESC` | Catalog browsing (most common query) |
| `idx_status_category` | `status ASC, category ASC` | Category-filtered catalog |
| `idx_instructorId_status` | `instructorId ASC, status ASC` | Instructor dashboard |
| `idx_tags` | `tags ASC` | Tag-based search |

**Indexes on `enrollments` collection:**

| Index name | Fields | Unique | Purpose |
|-----------|--------|--------|---------|
| `idx_student_course_unique` | `studentId, courseId` | Yes | Prevents duplicate enrollments |
| `idx_student_enrolledAt` | `studentId ASC, enrolledAt DESC` | No | Student's enrollment list |
| `idx_course_status` | `courseId ASC, status ASC` | No | Course enrollment analytics |

### 7.5 Repository Port (Dependency Inversion)

```
domain/repository/CourseRepository   ← interface (domain owns this)
             ↑ implements
infrastructure/persistence/adapter/CourseRepositoryAdapter
             ↓ uses
infrastructure/persistence/repository/MongoCourseCrudRepository
             ↓ extends
Spring Data MongoRepository<CourseDocument, String>
```

The application service only ever depends on the domain's `CourseRepository` interface — it has no knowledge of MongoDB, Spring Data, or `CourseDocument`.

---

## 8. Caching Strategy

Redis is used to cache individual course detail responses. The cache is managed by Spring's `@Cacheable` / `@CacheEvict` annotations.

| Operation | Cache behaviour |
|-----------|----------------|
| `GET /courses/v1/{courseId}` | `@Cacheable("courses", key="#courseId")` — cache-aside, 5-min TTL |
| `PUT /courses/v1/{courseId}` | `@CacheEvict("courses", key="#courseId")` — invalidates on update |
| `POST /courses/v1/{courseId}/publish` | `@CacheEvict("courses", key="#courseId")` — invalidates on publish |
| `POST /courses/v1/{courseId}/archive` | `@CacheEvict("courses", key="#courseId")` — invalidates on archive |
| `GET /courses/v1` (catalog) | NOT cached — search-service handles heavy catalog reads |

**Why not cache the catalog?** The paginated catalog is served with filters (`category`, `sortBy`, `page`) making the key space large and invalidation complex. The gateway's Redis rate limiter absorbs burst traffic on catalog browsing.

**Cache serialization:** Values are serialized as JSON using `GenericJackson2JsonRedisSerializer` with `JavaTimeModule` (for `Instant`) and default typing enabled (for correct polymorphic deserialization).

---

## 9. Messaging — Events Published

The service publishes to the `lms.course.events` **topic exchange**.

### 9.1 CoursePublishedEvent

**Trigger:** `POST /courses/v1/{courseId}/publish` succeeds
**Routing key:** `lms.course.published`
**Consumed by:** `search-service` (indexes the course for full-text search)

```json
{
  "eventId": "uuid",
  "courseId": "uuid",
  "title": "Spring Boot Microservices",
  "description": "...",
  "shortDescription": "...",
  "instructorId": "uuid",
  "instructorName": "Jane Doe",
  "category": "Programming",
  "language": "English",
  "level": "BEGINNER",
  "price": 49.99,
  "thumbnailUrl": "...",
  "tags": ["java", "spring"],
  "averageRating": 0.0,
  "ratingCount": 0,
  "totalEnrollments": 0,
  "publishedAt": "2026-01-15T10:00:00Z",
  "occurredAt": "2026-01-15T10:00:00Z"
}
```

### 9.2 StudentEnrolledEvent

**Trigger:** Student successfully enrolls in a course
**Routing key:** `lms.course.student.enrolled`
**Consumed by:** `progress-service` (creates progress tracking record)

```json
{
  "eventId": "uuid",
  "enrollmentId": "uuid",
  "courseId": "uuid",
  "courseTitle": "Spring Boot Microservices",
  "studentId": "uuid",
  "instructorId": "uuid",
  "occurredAt": "2026-01-15T10:00:00Z"
}
```

### 9.3 CourseArchivedEvent

**Trigger:** `POST /courses/v1/{courseId}/archive` succeeds
**Routing key:** `lms.course.archived`
**Consumed by:** `search-service` (removes course from search index)

```json
{
  "eventId": "uuid",
  "courseId": "uuid",
  "instructorId": "uuid",
  "occurredAt": "2026-01-15T10:00:00Z"
}
```

### 9.4 Publisher Implementation

Events are published using Spring AMQP's `RabbitTemplate`. Publishing is **best-effort** — failures are logged but do not roll back the database transaction. The course is saved to MongoDB first; the event is published after the save succeeds:

```java
// CourseService.publishCourse()
Course saved = courseRepository.save(course);   // 1. DB write (durable)
eventPublisher.publishCoursePublished(saved);   // 2. Event (best-effort)
```

This is an intentional tradeoff: we accept the possibility that a course is saved but the event is not published (e.g., RabbitMQ is temporarily down), rather than making the entire operation fail for a non-critical side effect. A production system would use the **Transactional Outbox Pattern** here for guaranteed delivery.

---

## 10. Messaging — Events Consumed

### 10.1 PaymentCompletedEvent

**Queue:** `lms.course.payment-completed`
**Bound to:** `lms.payment.events` exchange
**Routing key:** `lms.payment.completed`
**Published by:** `payment-service`

When a student pays for a course, `payment-service` publishes `PaymentCompletedEvent`. Course-service consumes it and triggers enrollment:

```
payment-service                 RabbitMQ                  course-service
     │                              │                          │
     │ PaymentCompletedEvent         │                          │
     │──────────────────────────────►│                          │
     │                              │ route to                 │
     │                              │ lms.course.payment-completed
     │                              │─────────────────────────►│
     │                              │                          │
     │                              │            EnrollmentService.enroll()
     │                              │                          │
     │                              │         basicAck()       │
     │                              │◄─────────────────────────│
```

**Message format consumed:**
```json
{
  "eventId": "uuid",
  "paymentId": "uuid",
  "courseId": "uuid",
  "studentId": "uuid",
  "amount": 49.99,
  "occurredAt": "2026-01-15T10:00:00Z"
}
```

**Consumer configuration:**
- **Acknowledge mode:** Manual (`basicAck` / `basicNack`)
- **Prefetch:** 10 messages
- **Retry policy:** On `TransientDataAccessException`, the message is NACKed with `requeue=true` (retried). On all other failures, `requeue=false` (message goes to DLQ).
- **DLQ:** `lms.course.dlq.exchange` with routing key `lms.course.dlq`

---

## 11. Course Lifecycle State Machine

```
                     ┌─────────────────────────────────────────────┐
                     │                                             │
         create()    │                                             │
     ────────────►  DRAFT  ──── publish() ──────────► PUBLISHED   │
                     │          (requires ≥1 section               │
                     │           and ≥1 lesson)                    │
                     │                                   │         │
                     │                              archive()      │
                     │                                   │         │
                     └────── archive() ────────────► ARCHIVED ─────┘
                                                         │
                                                    rePublish()
                                                    (admin only)
```

**Transition rules (enforced in `CourseStatus.canTransitionTo()`):**

| From | To | Allowed | Condition |
|------|----|---------|-----------|
| `DRAFT` | `PUBLISHED` | Yes | ≥1 section AND ≥1 lesson |
| `DRAFT` | `ARCHIVED` | Yes | None |
| `PUBLISHED` | `ARCHIVED` | Yes | None |
| `PUBLISHED` | `DRAFT` | No | — |
| `ARCHIVED` | `PUBLISHED` | Yes (re-publish) | None |
| `ARCHIVED` | `DRAFT` | No | — |

**Content mutation rules:**
- Only `DRAFT` courses can have sections/lessons added or removed
- A `PUBLISHED` or `ARCHIVED` course must be archived and re-drafted before content changes (edit → archive → re-create, or a future "edit draft" feature)

---

## 12. Enrollment Flow

### 12.1 Direct Enrollment (free courses)

```
Student: POST /courses/v1/{courseId}/enroll
         Authorization: Bearer <student-token>
              │
              ▼
    EnrollmentService.enroll()
              │
    1. Idempotency check: already enrolled? → return existing
    2. Load course from MongoDB
    3. Is course PUBLISHED? → if not, throw 409
    4. Enrollment.create(courseId, studentId, courseTitle, instructorId)
    5. enrollmentRepository.save(enrollment)
    6. courseService.incrementEnrollmentCount(courseId)
    7. eventPublisher.publishStudentEnrolled(enrollment)
              │
              ▼
    201 EnrollmentResponse
```

### 12.2 Post-Payment Enrollment (paid courses)

```
payment-service publishes PaymentCompletedEvent
              │
              ▼
    PaymentCompletedEventConsumer (RabbitMQ listener)
              │
    EnrollmentService.enroll(event.courseId, event.studentId)
              │  (same flow as direct enrollment)
              ▼
    basicAck() — message removed from queue
```

### 12.3 Idempotency Guarantee

Enrollment is **idempotent**. If the same `(studentId, courseId)` pair arrives twice — whether from a retry, a duplicate payment event, or a double-click — the service returns the existing enrollment without creating a duplicate. This is enforced by both a unique MongoDB index (`idx_student_course_unique`) and an application-level check before insert.

---

## 13. Inter-Service Communication

### 13.1 Inbound (synchronous)

Other services call this service's internal endpoints directly (bypassing the gateway):

| Caller | Endpoint | Purpose |
|--------|----------|---------|
| `progress-service` | `GET /courses/v1/internal/{courseId}` | Get course detail for progress UI |
| `progress-service` | `GET /courses/v1/internal/student/{studentId}/enrolled-courses` | List enrolled course IDs |
| `progress-service` | `GET /courses/v1/internal/{courseId}/is-enrolled/{studentId}` | Access gate check |

Internal endpoints are on the same base path but are `permitAll()` in the security config. They are only reachable within the Docker network — not exposed through the gateway.

### 13.2 Outbound (asynchronous)

Course-service does not call other services synchronously. All outbound communication is via RabbitMQ events (see Sections 9 and 10).

---

## 14. Observability

### 14.1 Distributed Tracing

All requests carry a distributed trace through OpenTelemetry. Trace IDs appear in every log line:

```
2026-03-28 08:57:37 INFO [course-service,fb4387a304476f92,c6e8470e220e4565]
    CourseEventPublisherImpl - Published CoursePublishedEvent courseId=0f32c151
                                         ^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^
                                         traceId           spanId
```

Traces are exported to **Tempo** and queryable in **Grafana → Explore → Tempo**.

### 14.2 Structured Logging

All logs include the service name, trace ID, and span ID. Logs are shipped to **Loki** via Logback HTTP appender and queryable in Grafana:

```
# Find all events for a specific course
{app="course-service"} |= "courseId=0f32c151"

# Watch for publish failures
{app="course-service"} |= "Failed to publish event"

# Watch enrollment activity
{app="course-service"} |= "Enrollment created"
```

### 14.3 Metrics

Prometheus metrics are exposed at `/actuator/prometheus`. Custom Micrometer counters are registered for key business events.

---

## 15. Configuration

### 15.1 Config Server Source

All environment-specific config is served by the Config Server at startup. The local override file is `config-server/src/main/resources/configs/course-service.yml`.

### 15.2 Key Properties

```yaml
server:
  port: 8082

spring:
  data:
    mongodb:
      uri: mongodb://${MONGO_USERNAME}:${MONGO_PASSWORD}@${MONGO_HOST}:27017/lms_courses?authSource=admin
      auto-index-creation: false   # indexes managed by MongoIndexInitializer

    redis:
      host: ${REDIS_HOST}
      port: 6379

  cache:
    type: redis
    redis:
      time-to-live: 300000   # 5 minutes

  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: 5672
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: /lms
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://${AUTH_SERVICE_HOST}:9000/auth/v1/.well-known/jwks.json
```

### 15.3 Environment Variables (Docker)

| Variable | Default | Description |
|----------|---------|-------------|
| `MONGO_HOST` | `mongodb` | MongoDB hostname |
| `MONGO_USERNAME` | `lms_user` | MongoDB username |
| `MONGO_PASSWORD` | `lms_password` | MongoDB password |
| `REDIS_HOST` | `redis` | Redis hostname |
| `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `RABBITMQ_USERNAME` | `lms_user` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `lms_password` | RabbitMQ password |
| `AUTH_SERVICE_HOST` | `auth-service` | Auth service hostname for JWKS |
| `CONFIG_SERVER_HOST` | `localhost` | Config Server hostname |

---

## 16. Key Design Decisions

### 16.1 Hexagonal Architecture — Why?

The domain model has **zero Spring or MongoDB annotations**. This was a deliberate choice to:
- Allow unit testing the domain with no Spring context (just `new Course(...)`)
- Keep business rules readable without framework noise
- Allow swapping MongoDB for PostgreSQL without touching the domain

**Evidence:** `Course.java` imports only `java.*` packages. No `@Entity`, `@Document`, `@Component`.

### 16.2 MongoDB — One Document per Course Hierarchy

Sections and lessons are embedded in the `CourseDocument`, not stored in separate collections. This is correct for the access pattern: you always read a complete course (never just one lesson in isolation). The tradeoff is that adding/removing a lesson rewrites the entire course document — acceptable since course mutations are infrequent compared to reads.

### 16.3 Enrollment in a Separate Collection

Despite Enrollment being related to Course, it is stored in a separate `enrollments` collection. If enrollments were embedded in the Course document, a popular course with 10,000 students would have a 50+ MB document — exceeding MongoDB's 16 MB limit and degrading all course reads.

### 16.4 Denormalization

Two fields are intentionally duplicated across documents:
- `instructorName` on `CourseDocument` — avoids fetching from user-service on every course read
- `courseTitle` and `instructorId` on `EnrollmentDocument` — avoids fetching from courses collection on enrollment list queries

This is a standard MongoDB read-optimization pattern. The tradeoff is that updating an instructor's name requires updating all their courses — acceptable since name changes are rare.

### 16.5 Best-Effort Event Publishing

Events are published **after** the MongoDB write, not in the same transaction. This means:
- If MongoDB write fails → no event published (correct behaviour)
- If MongoDB write succeeds but RabbitMQ fails → event is lost (acceptable for this service)

The proper solution for guaranteed delivery is the **Transactional Outbox Pattern** (as implemented in auth-service): write the event to a `outbox` table in the same MongoDB transaction, then a scheduler polls and publishes. This service uses direct publishing as a simpler tradeoff for a portfolio project.

### 16.6 Manual ACK on RabbitMQ Consumer

The `PaymentCompletedEventConsumer` uses **manual acknowledgment** (`acknowledge-mode: manual`). The message is only ACKed after the enrollment is successfully saved to MongoDB. If the service crashes mid-processing, the message remains unacknowledged and RabbitMQ will re-deliver it to another instance. Combined with the idempotent enrollment check, this gives **at-least-once delivery** with correct semantics.

---

## 17. Error Handling

All exceptions are handled by `GlobalExceptionHandler` (`@RestControllerAdvice`) which returns **RFC 9457 Problem Details** format.

### 17.1 Exception → HTTP Status Mapping

| Exception | HTTP Status | type suffix |
|-----------|-------------|-------------|
| `CourseNotFoundException` | `404 Not Found` | `course-not-found` |
| `CourseOperationException` | `409 Conflict` | `course-operation-error` |
| `UnauthorizedCourseAccessException` | `403 Forbidden` | `unauthorized-access` |
| `EnrollmentException` | `409 Conflict` | `enrollment-error` |
| `AccessDeniedException` (Spring Security) | `403 Forbidden` | `access-denied` |
| `MethodArgumentNotValidException` | `422 Unprocessable Entity` | `validation-error` |
| Any other `Exception` | `500 Internal Server Error` | `internal-error` |

### 17.2 Response Shape

```json
{
  "type": "https://lms-platform.com/errors/course-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Course not found: 550e8400-e29b-41d4-a716-446655440000",
  "instance": "/courses/v1/550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-03-28T09:00:00.000Z"
}
```

For validation errors, the `errors` map is included:
```json
{
  "type": "https://lms-platform.com/errors/validation-error",
  "title": "Validation Error",
  "status": 422,
  "detail": "Validation failed",
  "errors": {
    "title": "Title is required",
    "price": "Price must be 0 or greater"
  },
  "timestamp": "2026-03-28T09:00:00.000Z"
}
```

### 17.3 Consumer Error Handling

In `PaymentCompletedEventConsumer`, errors are classified by type before deciding whether to requeue:

```java
// Transient errors (DB timeout, network blip) → requeue for retry
boolean requeue = e instanceof TransientDataAccessException;
channel.basicNack(deliveryTag, false, requeue);

// All other errors (bad data, business rule violation) → send to DLQ
channel.basicNack(deliveryTag, false, false);
```
