package com.lms.paymentservice.application.dto.response;

import com.lms.paymentservice.domain.model.Payment;
import com.lms.paymentservice.domain.model.PaymentMethod;
import com.lms.paymentservice.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID          paymentId,
        UUID          userId,
        UUID          courseId,
        BigDecimal    amount,
        String        currency,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        String        transactionId,
        String        failureReason,
        Instant       createdAt,
        Instant       updatedAt,
        Instant       completedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getUserId(),
                payment.getCourseId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getTransactionId(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getCompletedAt()
        );
    }
}
