package com.lms.authservice.infrastructure.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.authservice.domain.model.OutboxEvent;
import com.lms.authservice.domain.repository.OutboxRepository;
import com.lms.authservice.infrastructure.messaging.RabbitMQConfig;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishEvents() {

        List<OutboxEvent> events =
                outboxRepository.findPendingEvents(50);

        for (OutboxEvent event : events) {

            try {

                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.AUTH_EXCHANGE,
                        RabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                        event.getPayload()
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