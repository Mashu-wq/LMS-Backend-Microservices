package com.lms.authservice.infrastructure.persistence.mapper;

import com.lms.authservice.domain.model.OutboxEvent;
import com.lms.authservice.infrastructure.persistence.entity.OutboxEventEntity;
import org.mapstruct.Mapper;

@Mapper
public interface OutboxEventPersistenceMapper {

    OutboxEventEntity toEntity(OutboxEvent event);

    default OutboxEvent toDomain(OutboxEventEntity entity) {

        return OutboxEvent.reconstitute(
                entity.getId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getProcessedAt()
        );
    }
}