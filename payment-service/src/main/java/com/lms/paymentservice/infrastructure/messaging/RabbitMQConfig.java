package com.lms.paymentservice.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for payment-service (producer only).
 *
 * <p>payment-service publishes to {@code lms.payment.events} topic exchange.
 * Downstream consumers bind their own queues:
 * <ul>
 *   <li>course-service: queue {@code lms.course.payment-completed},
 *       routing key {@code lms.payment.completed}</li>
 *   <li>notification-service: binds to both completed and failed keys</li>
 * </ul>
 *
 * <p>We declare the exchange here; consumers declare their own queues
 * and bindings (each service owns its own topology).
 */
@Configuration
public class RabbitMQConfig {

    // Exchange this service publishes to
    public static final String PAYMENT_EXCHANGE = "lms.payment.events";

    // Routing keys — must match what consumers bind on
    public static final String PAYMENT_COMPLETED_KEY     = "lms.payment.completed";
    public static final String PAYMENT_FAILED_KEY        = "lms.payment.failed";
    public static final String REFUND_COMPLETED_KEY      = "lms.payment.refund.completed";

    @Bean
    public TopicExchange paymentEventsExchange() {
        return ExchangeBuilder.topicExchange(PAYMENT_EXCHANGE).durable(true).build();
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }
}
