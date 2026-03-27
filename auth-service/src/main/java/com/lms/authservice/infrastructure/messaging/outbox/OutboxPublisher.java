package com.lms.authservice.infrastructure.messaging.outbox;

import com.lms.authservice.domain.model.OutboxEvent;
import com.lms.authservice.domain.repository.OutboxRepository;
import com.lms.authservice.infrastructure.messaging.RabbitMQConfig;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishEvents() {

        List<OutboxEvent> events =
                outboxRepository.findPendingEvents(50);

        for (OutboxEvent event : events) {

            try {

                // Send raw JSON bytes — payload is already serialized JSON from the outbox.
                // Using convertAndSend(String) would double-serialize it (JSON string inside
                // JSON string) and set __TypeId__=java.lang.String, breaking consumers.
                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .andProperties(new MessageProperties())
                        .build();
                message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                message.getMessageProperties().setContentEncoding("UTF-8");

                rabbitTemplate.send(
                        RabbitMQConfig.AUTH_EXCHANGE,
                        RabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                        message
                );

                event.markProcessed();
                outboxRepository.markProcessed(event);

            } catch (Exception e) {

                log.error("Failed to publish event {}", event.getId());

                event.markFailed();
                outboxRepository.markFailed(event);
            }
        }
    }
}