package com.lms.paymentservice.infrastructure.persistence.mapper;

import com.lms.paymentservice.domain.model.Payment;
import com.lms.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentPersistenceMapper {

    @Mapping(target = "paymentId",      source = "paymentId")
    @Mapping(target = "userId",         source = "userId")
    @Mapping(target = "courseId",       source = "courseId")
    @Mapping(target = "amount",         source = "amount")
    @Mapping(target = "currency",       source = "currency")
    @Mapping(target = "status",         source = "status")
    @Mapping(target = "paymentMethod",  source = "paymentMethod")
    @Mapping(target = "transactionId",  source = "transactionId")
    @Mapping(target = "failureReason",  source = "failureReason")
    @Mapping(target = "idempotencyKey", source = "idempotencyKey")
    @Mapping(target = "createdAt",      source = "createdAt")
    @Mapping(target = "updatedAt",      source = "updatedAt")
    @Mapping(target = "completedAt",    source = "completedAt")
    PaymentEntity toEntity(Payment payment);

    default Payment toDomain(PaymentEntity entity) {
        return Payment.reconstitute(
                entity.getPaymentId(),
                entity.getUserId(),
                entity.getCourseId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getPaymentMethod(),
                entity.getTransactionId(),
                entity.getFailureReason(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCompletedAt()
        );
    }
}
