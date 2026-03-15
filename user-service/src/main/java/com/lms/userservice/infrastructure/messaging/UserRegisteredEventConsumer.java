package com.lms.userservice.infrastructure.messaging;

import com.lms.userservice.application.service.UserProfileService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumer for UserRegisteredEvent published by auth-service.
 *
 * <p>Acknowledgement strategy (MANUAL mode):
 * - SUCCESS → basicAck: message removed from queue.
 * - BUSINESS ERROR (e.g., profile already exists) → basicAck: idempotent, discard.
 * - TRANSIENT ERROR (DB unavailable) → basicNack with requeue=true: retry.
 * - PERMANENT ERROR (bad message format) → basicNack with requeue=false: goes to DLQ.
 *
 * <p>This prevents infinite retry loops on bad messages while ensuring
 * transient failures are retried automatically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

    private final UserProfileService userProfileService;

    @RabbitListener(
        queues = RabbitMQConfig.USER_PROFILE_CREATE_QUEUE,
        containerFactory = "rabbitListenerContainerFactory"
    )
    public void handleUserRegistered(UserRegisteredEvent event,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("Received UserRegisteredEvent eventId={} userId={}", event.eventId(), event.userId());

        try {
            userProfileService.createProfile(
                    event.userId(),
                    event.email(),
                    event.firstName(),
                    event.lastName(),
                    event.role()
            );

            channel.basicAck(deliveryTag, false);
            log.info("UserRegisteredEvent processed successfully userId={}", event.userId());

        } catch (Exception e) {
            log.error("Failed to process UserRegisteredEvent userId={}: {}",
                    event.userId(), e.getMessage(), e);

            // Requeue for transient errors (DB connection issues etc.)
            // For idempotent duplicate case, UserProfileService handles it gracefully
            boolean requeue = isTransientError(e);
            channel.basicNack(deliveryTag, false, requeue);

            if (!requeue) {
                log.error("Message sent to DLQ for userId={}", event.userId());
            }
        }
    }

    private boolean isTransientError(Exception e) {
        // Treat DB connection errors as transient → requeue
        return e instanceof org.springframework.dao.TransientDataAccessException
            || e instanceof org.springframework.dao.RecoverableDataAccessException;
    }
}
