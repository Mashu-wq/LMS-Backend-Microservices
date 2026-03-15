package com.lms.authservice.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event — published after a user successfully registers.
 * Consumed by: notification-service (welcome email), user-service (profile creation).
 */
public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        Instant occurredAt
) {
    public static UserRegisteredEvent of(UUID userId, String email,
                                         String firstName, String lastName, String role) {
        return new UserRegisteredEvent(
                UUID.randomUUID(), userId, email, firstName, lastName, role, Instant.now()
        );
    }
}
