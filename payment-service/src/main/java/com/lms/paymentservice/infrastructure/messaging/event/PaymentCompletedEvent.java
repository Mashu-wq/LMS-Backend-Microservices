package com.lms.paymentservice.infrastructure.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a payment completes successfully.
 *
 * <p>course-service consumes this event (queue: lms.course.payment-completed)
 * to activate the student's enrollment. notification-service consumes it to
 * send a payment confirmation email.
 *
 * <p>Each consumer maintains a local copy of this record — no shared library.
 * The contract is maintained via documentation and consumer-driven contract tests.
 */
public record PaymentCompletedEvent(
        UUID       eventId,
        UUID       paymentId,
        UUID       userId,
        UUID       courseId,
        BigDecimal amount,
        String     currency,
        String     transactionId,
        Instant    occurredAt
) {
    public static PaymentCompletedEvent of(UUID paymentId, UUID userId, UUID courseId,
                                            BigDecimal amount, String currency,
                                            String transactionId) {
        return new PaymentCompletedEvent(
                UUID.randomUUID(), paymentId, userId, courseId,
                amount, currency, transactionId, Instant.now());
    }
}
