package com.lms.paymentservice.domain.repository;

import com.lms.paymentservice.domain.model.Payment;
import com.lms.paymentservice.domain.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> findByUserId(UUID userId, Pageable pageable);

    Page<Payment> findAll(Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    boolean existsByUserIdAndCourseIdAndStatus(UUID userId, UUID courseId, PaymentStatus status);
}
