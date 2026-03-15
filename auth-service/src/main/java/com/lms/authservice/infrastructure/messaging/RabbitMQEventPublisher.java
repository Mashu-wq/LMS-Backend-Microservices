package com.lms.authservice.infrastructure.messaging;

import com.lms.authservice.application.port.EventPublisher;
import com.lms.authservice.domain.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ implementation of EventPublisher port.
 *
 * <p>Event publishing is best-effort from the application service's perspective.
 * For strict at-least-once guarantees, consider the Transactional Outbox pattern:
 * write the event to a DB table in the same transaction as the user save,
 * then a separate process reads and publishes. That eliminates the window
 * where user saves but event fails to publish.
 *
 * <p>For this portfolio project, direct publishing is used with logging.
 * The Outbox pattern is noted here as a production enhancement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishUserRegistered(UserRegisteredEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AUTH_EXCHANGE,
                    RabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                    event
            );
            log.info("Published UserRegisteredEvent for userId={} eventId={}", event.userId(), event.eventId());
        } catch (Exception e) {
            // Do NOT let event publishing failure roll back the registration transaction.
            // The user IS registered; the event is best-effort.
            // In production: use Outbox pattern for guaranteed delivery.
            log.error("Failed to publish UserRegisteredEvent for userId={} eventId={}: {}",
                    event.userId(), event.eventId(), e.getMessage());
        }
    }
}
