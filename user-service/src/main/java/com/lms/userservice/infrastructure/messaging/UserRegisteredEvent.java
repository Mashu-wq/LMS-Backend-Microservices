package com.lms.userservice.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Incoming event contract from auth-service.
 * Must match auth-service's UserRegisteredEvent exactly — this is the integration contract.
 *
 * <p>Design note: we define a local copy rather than sharing a library.
 * Sharing an "events" module creates tight coupling between services at compile time.
 * Local copies allow each service to evolve independently, with backwards-compatible
 * field additions handled by Jackson's lenient deserialization.
 */
public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        Instant occurredAt
) {}
