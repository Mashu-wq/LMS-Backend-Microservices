package com.lms.paymentservice.infrastructure.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a refund is processed successfully.
 *
 * <p>notification-service consumes this to send a refund confirmation email.
 * course-service consumes this to revoke the student's enrollment.
 */
public record RefundCompletedEvent(
        UUID       eventId,
        UUID       paymentId,
        UUID       userId,
        UUID       courseId,
        BigDecimal amount,
        String     currency,
        Instant    occurredAt
) {
    public static RefundCompletedEvent of(UUID paymentId, UUID userId, UUID courseId,
                                           BigDecimal amount, String currency) {
        return new RefundCompletedEvent(
                UUID.randomUUID(), paymentId, userId, courseId,
                amount, currency, Instant.now());
    }
}
