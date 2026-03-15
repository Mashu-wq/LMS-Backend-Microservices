package com.lms.paymentservice.infrastructure.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a payment attempt fails.
 *
 * <p>notification-service consumes this to send a payment failure email.
 * course-service may consume it to roll back a reserved seat if reservations
 * are implemented in the future.
 */
public record PaymentFailedEvent(
        UUID       eventId,
        UUID       paymentId,
        UUID       userId,
        UUID       courseId,
        BigDecimal amount,
        String     currency,
        String     failureReason,
        Instant    occurredAt
) {
    public static PaymentFailedEvent of(UUID paymentId, UUID userId, UUID courseId,
                                         BigDecimal amount, String currency,
                                         String failureReason) {
        return new PaymentFailedEvent(
                UUID.randomUUID(), paymentId, userId, courseId,
                amount, currency, failureReason, Instant.now());
    }
}
