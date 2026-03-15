package com.lms.authservice.infrastructure.persistence.adapter;

import com.lms.authservice.domain.model.OutboxEvent;
import com.lms.authservice.domain.repository.OutboxRepository;
import com.lms.authservice.infrastructure.persistence.entity.OutboxEventEntity;
import com.lms.authservice.infrastructure.persistence.mapper.OutboxEventPersistenceMapper;
import com.lms.authservice.infrastructure.persistence.repository.JpaOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OutboxRepositoryAdapter implements OutboxRepository {

    private final JpaOutboxRepository jpaRepository;
    private final OutboxEventPersistenceMapper mapper;

    @Override
    public OutboxEvent save(OutboxEvent event) {

        OutboxEventEntity entity = mapper.toEntity(event);

        return mapper.toDomain(
                jpaRepository.save(entity)
        );
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {

        return jpaRepository
                .findTop50ByStatusOrderByCreatedAtAsc("PENDING")
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void markProcessed(OutboxEvent event) {

        OutboxEventEntity entity = mapper.toEntity(event);

        jpaRepository.save(entity);
    }

    @Override
    public void markFailed(OutboxEvent event) {

        OutboxEventEntity entity = mapper.toEntity(event);

        jpaRepository.save(entity);
    }
}