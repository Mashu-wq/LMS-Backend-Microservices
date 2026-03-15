package com.lms.paymentservice.domain.exception;

import java.util.UUID;

public class DuplicatePaymentException extends RuntimeException {

    public DuplicatePaymentException(UUID userId, UUID courseId) {
        super("User " + userId + " has already completed a payment for course " + courseId);
    }
}
