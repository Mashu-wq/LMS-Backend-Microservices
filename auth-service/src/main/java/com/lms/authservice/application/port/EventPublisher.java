package com.lms.authservice.application.port;

import com.lms.authservice.domain.event.UserRegisteredEvent;

/**
 * Port for publishing domain events.
 * Decouples the application layer from RabbitMQ / any specific broker.
 */
public interface EventPublisher {

    void publishUserRegistered(UserRegisteredEvent event);
}
