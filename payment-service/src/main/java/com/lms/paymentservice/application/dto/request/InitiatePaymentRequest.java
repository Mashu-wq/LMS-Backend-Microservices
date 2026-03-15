package com.lms.paymentservice.application.dto.request;

import com.lms.paymentservice.domain.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiatePaymentRequest(

        @NotNull(message = "courseId is required")
        UUID courseId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod,

        /**
         * Client-provided idempotency key for safe retries.
         * If null, the service generates one from userId + courseId.
         */
        @Size(max = 255, message = "idempotencyKey must be at most 255 characters")
        String idempotencyKey
) {
    public InitiatePaymentRequest {
        if (currency == null || currency.isBlank()) {
            currency = "USD";
        }
    }
}
