package com.lms.notificationservice.infrastructure.messaging;

import com.lms.notificationservice.application.dto.EmailMessage;
import com.lms.notificationservice.application.service.EmailService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.USER_REGISTERED_QUEUE)
    public void handleUserRegistered(UserRegisteredEvent event,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.info("Sending welcome email to userId={} email={}", event.userId(), event.email());
        try {
            emailService.send(new EmailMessage(
                    event.email(),
                    "Welcome to LMS Platform!",
                    "welcome",
                    Map.of(
                            "firstName", event.firstName(),
                            "email",     event.email(),
                            "role",      event.role()
                    )
            ));
            channel.basicAck(deliveryTag, false);
        } catch (EmailService.EmailDeliveryException e) {
            log.error("Failed to send welcome email to {}: {}", event.email(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);  // requeue — SMTP may recover
        } catch (Exception e) {
            log.error("Permanent error processing UserRegisteredEvent userId={}: {}",
                    event.userId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);  // → DLQ
        }
    }

    public record UserRegisteredEvent(
            UUID    eventId,
            UUID    userId,
            String  email,
            String  firstName,
            String  lastName,
            String  role,
            Instant occurredAt
    ) {}
}
