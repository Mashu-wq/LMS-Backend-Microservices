package com.lms.paymentservice.domain.model;

import com.lms.paymentservice.domain.exception.PaymentAlreadyProcessedException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment aggregate root.
 *
 * <p>All state transitions are enforced here — no external code can set
 * {@code status} directly. This keeps the lifecycle rules in one place
 * and makes invalid transitions impossible at the domain level.
 *
 * <p>The {@code idempotencyKey} is the caller's guarantee against duplicate
 * charges. It is typically {@code userId + ":" + courseId} or a UUID the
 * client generates and retains for safe retries.
 */
@Getter
public class Payment {

    private final UUID          paymentId;
    private final UUID          userId;
    private final UUID          courseId;
    private final BigDecimal    amount;
    private final String        currency;
    private       PaymentStatus status;
    private final PaymentMethod paymentMethod;
    private       String        transactionId;   // set by gateway on success
    private       String        failureReason;   // set on FAILED
    private final String        idempotencyKey;
    private final Instant       createdAt;
    private       Instant       updatedAt;
    private       Instant       completedAt;

    private Payment(UUID paymentId, UUID userId, UUID courseId,
                    BigDecimal amount, String currency,
                    PaymentStatus status, PaymentMethod paymentMethod,
                    String transactionId, String failureReason,
                    String idempotencyKey,
                    Instant createdAt, Instant updatedAt, Instant completedAt) {
        this.paymentId      = paymentId;
        this.userId         = userId;
        this.courseId       = courseId;
        this.amount         = amount;
        this.currency       = currency;
        this.status         = status;
        this.paymentMethod  = paymentMethod;
        this.transactionId  = transactionId;
        this.failureReason  = failureReason;
        this.idempotencyKey = idempotencyKey;
        this.createdAt      = createdAt;
        this.updatedAt      = updatedAt;
        this.completedAt    = completedAt;
    }

    // ── Factory: new payment ────────────────────────────────────────────────

    public static Payment create(UUID userId, UUID courseId,
                                 BigDecimal amount, String currency,
                                 PaymentMethod paymentMethod,
                                 String idempotencyKey) {
        Instant now = Instant.now();
        return new Payment(
                UUID.randomUUID(), userId, courseId,
                amount, currency,
                PaymentStatus.PENDING, paymentMethod,
                null, null,
                idempotencyKey,
                now, now, null
        );
    }

    // ── Factory: rehydrate from persistence ────────────────────────────────

    public static Payment reconstitute(UUID paymentId, UUID userId, UUID courseId,
                                       BigDecimal amount, String currency,
                                       PaymentStatus status, PaymentMethod paymentMethod,
                                       String transactionId, String failureReason,
                                       String idempotencyKey,
                                       Instant createdAt, Instant updatedAt,
                                       Instant completedAt) {
        return new Payment(paymentId, userId, courseId,
                amount, currency, status, paymentMethod,
                transactionId, failureReason, idempotencyKey,
                createdAt, updatedAt, completedAt);
    }

    // ── State transitions ───────────────────────────────────────────────────

    public void markProcessing() {
        requireStatus(PaymentStatus.PENDING);
        this.status    = PaymentStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void complete(String transactionId) {
        requireStatus(PaymentStatus.PROCESSING);
        this.status        = PaymentStatus.COMPLETED;
        this.transactionId = transactionId;
        this.completedAt   = Instant.now();
        this.updatedAt     = this.completedAt;
    }

    public void fail(String reason) {
        requireStatus(PaymentStatus.PROCESSING);
        this.status        = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt     = Instant.now();
    }

    public void requestRefund() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new PaymentAlreadyProcessedException(
                    "Refund can only be requested for COMPLETED payments, current status: " + this.status);
        }
        this.status    = PaymentStatus.REFUND_PENDING;
        this.updatedAt = Instant.now();
    }

    public void completeRefund() {
        requireStatus(PaymentStatus.REFUND_PENDING);
        this.status    = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private void requireStatus(PaymentStatus expected) {
        if (this.status != expected) {
            throw new PaymentAlreadyProcessedException(
                    "Expected status " + expected + " but was " + this.status);
        }
    }
}
