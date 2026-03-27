package com.lms.userservice.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for user-service.
 *
 * <p>This service is a CONSUMER of lms.auth.events exchange.
 * It declares its own queue (lms.user.user-registered) and binds to the
 * auth exchange. This is the "competing consumers" pattern — if we scale
 * user-service to 3 instances, RabbitMQ round-robins messages across them.
 *
 * <p>Dead Letter Queue: failed messages (after max retries) go to lms.user.dlq
 * for manual inspection. We never silently discard messages.
 */
@Configuration
public class RabbitMQConfig {

    // Consuming from auth-service exchange
    public static final String AUTH_EXCHANGE = "lms.auth.events";
    public static final String USER_REGISTERED_ROUTING_KEY = "lms.auth.user.registered";

    // user-service queues
    public static final String USER_PROFILE_CREATE_QUEUE = "lms.user.user-registered";
    public static final String USER_SERVICE_DLQ = "lms.user.dlq";
    public static final String USER_SERVICE_DLQ_EXCHANGE = "lms.user.dlq.exchange";

    @Bean
    public Queue userProfileCreateQueue() {
        return QueueBuilder
                .durable(USER_PROFILE_CREATE_QUEUE)
                .withArgument("x-dead-letter-exchange", USER_SERVICE_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", USER_SERVICE_DLQ)
                .withArgument("x-message-ttl", 300_000)   // 5 min max in queue
                .build();
    }

    @Bean
    public TopicExchange authEventsExchange() {
        return ExchangeBuilder
                .topicExchange(AUTH_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Binding userProfileCreateBinding() {
        return BindingBuilder
                .bind(userProfileCreateQueue())
                .to(authEventsExchange())
                .with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public DirectExchange userServiceDlqExchange() {
        return ExchangeBuilder.directExchange(USER_SERVICE_DLQ_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue userServiceDlQueue() {
        return QueueBuilder.durable(USER_SERVICE_DLQ).build();
    }

    @Bean
    public Binding userServiceDlqBinding() {
        return BindingBuilder.bind(userServiceDlQueue())
                .to(userServiceDlqExchange())
                .with(USER_SERVICE_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        // Use the listener method's parameter type for deserialization rather than the
        // __TypeId__ header. This is required because auth-service's OutboxPublisher sends
        // raw JSON bytes (no __TypeId__ header) to guarantee no double-serialization.
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false); // Reject → DLQ (not requeue infinitely)
        return factory;
    }
}
