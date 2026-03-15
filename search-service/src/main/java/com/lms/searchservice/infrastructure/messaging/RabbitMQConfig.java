package com.lms.searchservice.infrastructure.messaging;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for search-service (pure consumer).
 *
 * <p>Binds to the course-service exchange declared by course-service.
 * search-service declares its own queues and dead-letter infrastructure.
 *
 * <p>Queue bindings:
 * <pre>
 *   lms.course.events --[lms.course.published]--> lms.search.course-published
 *   lms.course.events --[lms.course.archived]---> lms.search.course-archived
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    // Source exchange (declared by course-service; we just bind to it)
    public static final String COURSE_EXCHANGE      = "lms.course.events";
    public static final String COURSE_PUBLISHED_KEY = "lms.course.published";
    public static final String COURSE_ARCHIVED_KEY  = "lms.course.archived";

    // Our queues
    public static final String SEARCH_COURSE_PUBLISHED_QUEUE = "lms.search.course-published";
    public static final String SEARCH_COURSE_ARCHIVED_QUEUE  = "lms.search.course-archived";

    // Dead-letter infrastructure
    private static final String DLQ_EXCHANGE = "lms.search.dlq.exchange";
    private static final String DLQ_QUEUE    = "lms.search.dlq";

    // ── Exchanges ─────────────────────────────────────────────

    /** Declare so broker creates it if not yet present (idempotent). */
    @Bean
    public TopicExchange courseEventsExchange() {
        return new TopicExchange(COURSE_EXCHANGE, true, false);
    }

    // ── Dead-letter infrastructure ────────────────────────────

    @Bean
    public org.springframework.amqp.core.DirectExchange searchDlqExchange() {
        return new org.springframework.amqp.core.DirectExchange(DLQ_EXCHANGE, true, false);
    }

    @Bean
    public Queue searchDlq() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding searchDlqBinding() {
        return BindingBuilder.bind(searchDlq())
                .to(searchDlqExchange())
                .with(DLQ_QUEUE);
    }

    // ── Consumer queues ───────────────────────────────────────

    @Bean
    public Queue coursePublishedQueue() {
        return QueueBuilder.durable(SEARCH_COURSE_PUBLISHED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_QUEUE)
                .withArgument("x-message-ttl", 300_000)
                .build();
    }

    @Bean
    public Queue courseArchivedQueue() {
        return QueueBuilder.durable(SEARCH_COURSE_ARCHIVED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_QUEUE)
                .withArgument("x-message-ttl", 300_000)
                .build();
    }

    @Bean
    public Binding coursePublishedBinding() {
        return BindingBuilder.bind(coursePublishedQueue())
                .to(courseEventsExchange())
                .with(COURSE_PUBLISHED_KEY);
    }

    @Bean
    public Binding courseArchivedBinding() {
        return BindingBuilder.bind(courseArchivedQueue())
                .to(courseEventsExchange())
                .with(COURSE_ARCHIVED_KEY);
    }

    // ── Serialization & container factory ────────────────────

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(5);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
