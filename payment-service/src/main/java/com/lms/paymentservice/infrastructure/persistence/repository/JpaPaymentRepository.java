package com.lms.paymentservice.infrastructure.persistence.repository;

import com.lms.paymentservice.domain.model.PaymentStatus;
import com.lms.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Page<PaymentEntity> findByUserId(UUID userId, Pageable pageable);

    Page<PaymentEntity> findByStatus(PaymentStatus status, Pageable pageable);

    boolean existsByUserIdAndCourseIdAndStatus(UUID userId, UUID courseId, PaymentStatus status);
}
