package com.lms.courseservice.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for course-service (producer).
 *
 * <p>course-service publishes to lms.course.events topic exchange.
 * Consumers (notification-service, search-service, progress-service)
 * bind their own queues with appropriate routing keys.
 */
@Configuration
public class RabbitMQConfig {

    public static final String COURSE_EXCHANGE = "lms.course.events";
    public static final String COURSE_PUBLISHED_KEY = "lms.course.published";
    public static final String COURSE_ARCHIVED_KEY  = "lms.course.archived";
    public static final String STUDENT_ENROLLED_KEY = "lms.course.student.enrolled";

    // Also consume from payment-service for enrollment after payment
    public static final String PAYMENT_EXCHANGE = "lms.payment.events";
    public static final String PAYMENT_COMPLETED_KEY = "lms.payment.completed";
    public static final String COURSE_PAYMENT_QUEUE = "lms.course.payment-completed";

    @Bean
    public TopicExchange courseEventsExchange() {
        return ExchangeBuilder.topicExchange(COURSE_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return ExchangeBuilder.topicExchange(PAYMENT_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue coursePaymentQueue() {
        return QueueBuilder.durable(COURSE_PAYMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", "lms.course.dlq.exchange")
                .withArgument("x-dead-letter-routing-key", "lms.course.dlq")
                .build();
    }

    @Bean
    public Binding coursePaymentBinding() {
        return BindingBuilder.bind(coursePaymentQueue())
                .to(paymentEventsExchange())
                .with(PAYMENT_COMPLETED_KEY);
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
