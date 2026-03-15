package com.lms.authservice.infrastructure.messaging;


import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology configuration for auth-service.
 *
 * <p>Exchange strategy: topic exchange per domain (lms.auth.events).
 * Routing keys: lms.auth.user.registered, lms.auth.user.password-changed, etc.
 *
 * <p>Each consumer (notification-service, user-service) declares their own queue
 * and binds to this exchange with their routing key. This decouples producers
 * from consumers — new consumers can subscribe without changing auth-service.
 *
 * <p>Dead Letter Queue (DLQ): messages that fail processing go to lms.auth.dlq.
 * A separate consumer reads DLQ for alerting and manual review.
 */
@Configuration
public class RabbitMQConfig {

    public static final String AUTH_EXCHANGE = "lms.auth.events";
    public static final String USER_REGISTERED_ROUTING_KEY = "lms.auth.user.registered";

    // Dead Letter infrastructure
    public static final String AUTH_DLQ_EXCHANGE = "lms.auth.dlq.exchange";
    public static final String AUTH_DLQ_QUEUE = "lms.auth.dlq";

    @Bean
    public TopicExchange authEventsExchange() {
        return ExchangeBuilder
                .topicExchange(AUTH_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange authDlqExchange() {
        return ExchangeBuilder
                .directExchange(AUTH_DLQ_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue authDlQueue() {
        return QueueBuilder
                .durable(AUTH_DLQ_QUEUE)
                .build();
    }

    @Bean
    public Binding authDlqBinding() {
        return BindingBuilder
                .bind(authDlQueue())
                .to(authDlqExchange())
                .with(AUTH_DLQ_QUEUE);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // Enable publisher confirms for at-least-once delivery
        template.setMandatory(true);
        return template;
    }
}
