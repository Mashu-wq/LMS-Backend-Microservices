package com.lms.paymentservice.domain.model;

/**
 * Lifecycle of a payment:
 *
 * <pre>
 *   PENDING → PROCESSING → COMPLETED
 *                       ↘ FAILED
 *   COMPLETED → REFUND_PENDING → REFUNDED
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUND_PENDING,
    REFUNDED
}
