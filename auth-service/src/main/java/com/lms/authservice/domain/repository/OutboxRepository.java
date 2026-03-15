package com.lms.authservice.domain.repository;

import com.lms.authservice.domain.model.OutboxEvent;

import java.util.List;

public interface OutboxRepository {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findPendingEvents(int limit);

    void markProcessed(OutboxEvent event);

    void markFailed(OutboxEvent event);
}