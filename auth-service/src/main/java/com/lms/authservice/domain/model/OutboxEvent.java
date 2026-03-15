package com.lms.authservice.domain.model;

import java.time.Instant;
import java.util.UUID;

public class OutboxEvent {

    private final UUID id;
    private final String aggregateType;
    private final UUID aggregateId;
    private final String eventType;
    private final String payload;

    private Instant createdAt;
    private String status;
    private int retryCount;
    private Instant processedAt;

    private OutboxEvent(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload
    ) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;

        this.createdAt = Instant.now();
        this.status = "PENDING";
        this.retryCount = 0;
    }

    public static OutboxEvent create(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload
    ) {
        return new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                payload
        );
    }

    /**
     * Used by repository when loading from DB
     */
    public static OutboxEvent reconstitute(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            String status,
            int retryCount,
            Instant createdAt,
            Instant processedAt
    ) {

        OutboxEvent event = new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload
        );

        event.status = status;
        event.retryCount = retryCount;
        event.createdAt = createdAt;
        event.processedAt = processedAt;

        return event;
    }

    public void markProcessed() {
        this.status = "SENT";
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        this.retryCount++;
        this.status = "FAILED";
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}