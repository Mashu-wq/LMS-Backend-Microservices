package com.lms.notificationservice.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for notification-service — PURE CONSUMER.
 *
 * <p>Binds to events from auth, payment, and progress exchanges.
 * Each event type gets its own queue so they can be scaled or
 * paused independently without affecting other notification types.
 *
 * <pre>
 *  lms.auth.events    [user.registered]    → lms.notification.user-registered
 *  lms.payment.events [payment.completed]  → lms.notification.payment-completed
 *  lms.payment.events [payment.failed]     → lms.notification.payment-failed
 *  lms.payment.events [refund.completed]   → lms.notification.refund-completed
 *  lms.progress.events[course.completed]   → lms.notification.course-completed
 *
 *  All queues dead-letter to: lms.notification.dlq
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    // ── Source exchanges (declared by the producing services) ───────────────
    public static final String AUTH_EXCHANGE     = "lms.auth.events";
    public static final String PAYMENT_EXCHANGE  = "lms.payment.events";
    public static final String PROGRESS_EXCHANGE = "lms.progress.events";

    // ── Routing keys (must match producer definitions) ──────────────────────
    public static final String USER_REGISTERED_KEY   = "lms.auth.user.registered";
    public static final String PAYMENT_COMPLETED_KEY = "lms.payment.completed";
    public static final String PAYMENT_FAILED_KEY    = "lms.payment.failed";
    public static final String REFUND_COMPLETED_KEY  = "lms.payment.refund.completed";
    public static final String COURSE_COMPLETED_KEY  = "lms.progress.course.completed";

    // ── notification-service queues ─────────────────────────────────────────
    public static final String USER_REGISTERED_QUEUE   = "lms.notification.user-registered";
    public static final String PAYMENT_COMPLETED_QUEUE = "lms.notification.payment-completed";
    public static final String PAYMENT_FAILED_QUEUE    = "lms.notification.payment-failed";
    public static final String REFUND_COMPLETED_QUEUE  = "lms.notification.refund-completed";
    public static final String COURSE_COMPLETED_QUEUE  = "lms.notification.course-completed";

    // ── Dead-letter ─────────────────────────────────────────────────────────
    public static final String NOTIFICATION_DLQ          = "lms.notification.dlq";
    public static final String NOTIFICATION_DLQ_EXCHANGE = "lms.notification.dlq.exchange";

    // ── Source exchange references (declare so RabbitMQ creates them if absent)
    @Bean public TopicExchange authEventsExchange() {
        return ExchangeBuilder.topicExchange(AUTH_EXCHANGE).durable(true).build();
    }
    @Bean public TopicExchange paymentEventsExchange() {
        return ExchangeBuilder.topicExchange(PAYMENT_EXCHANGE).durable(true).build();
    }
    @Bean public TopicExchange progressEventsExchange() {
        return ExchangeBuilder.topicExchange(PROGRESS_EXCHANGE).durable(true).build();
    }

    // ── Dead-letter infrastructure ──────────────────────────────────────────
    @Bean public DirectExchange notificationDlqExchange() {
        return ExchangeBuilder.directExchange(NOTIFICATION_DLQ_EXCHANGE).durable(true).build();
    }
    @Bean public Queue notificationDlQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }
    @Bean public Binding notificationDlqBinding() {
        return BindingBuilder.bind(notificationDlQueue())
                .to(notificationDlqExchange()).with(NOTIFICATION_DLQ);
    }

    // ── Per-event queues & bindings ─────────────────────────────────────────

    @Bean
    public Queue userRegisteredQueue() { return buildQueue(USER_REGISTERED_QUEUE); }

    @Bean
    public Queue paymentCompletedQueue() { return buildQueue(PAYMENT_COMPLETED_QUEUE); }

    @Bean
    public Queue paymentFailedQueue() { return buildQueue(PAYMENT_FAILED_QUEUE); }

    @Bean
    public Queue refundCompletedQueue() { return buildQueue(REFUND_COMPLETED_QUEUE); }

    @Bean
    public Queue courseCompletedQueue() { return buildQueue(COURSE_COMPLETED_QUEUE); }

    @Bean
    public Binding userRegisteredBinding() {
        return BindingBuilder.bind(userRegisteredQueue())
                .to(authEventsExchange()).with(USER_REGISTERED_KEY);
    }
    @Bean
    public Binding paymentCompletedBinding() {
        return BindingBuilder.bind(paymentCompletedQueue())
                .to(paymentEventsExchange()).with(PAYMENT_COMPLETED_KEY);
    }
    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentFailedQueue())
                .to(paymentEventsExchange()).with(PAYMENT_FAILED_KEY);
    }
    @Bean
    public Binding refundCompletedBinding() {
        return BindingBuilder.bind(refundCompletedQueue())
                .to(paymentEventsExchange()).with(REFUND_COMPLETED_KEY);
    }
    @Bean
    public Binding courseCompletedBinding() {
        return BindingBuilder.bind(courseCompletedQueue())
                .to(progressEventsExchange()).with(COURSE_COMPLETED_KEY);
    }

    // ── Shared infrastructure ───────────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(5);
        factory.setDefaultRequeueRejected(false);   // failures → DLQ
        return factory;
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private Queue buildQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ)
                .withArgument("x-message-ttl", 300_000)
                .build();
    }
}
