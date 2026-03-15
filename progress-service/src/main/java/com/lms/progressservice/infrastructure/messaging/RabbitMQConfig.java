package com.lms.progressservice.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for progress-service.
 *
 * <p>CONSUMES: StudentEnrolledEvent from {@code lms.course.events} exchange
 * (routing key {@code lms.course.student.enrolled}) — triggers progress initialisation.
 *
 * <p>PRODUCES: CourseCompletedEvent to {@code lms.progress.events} exchange
 * (routing key {@code lms.progress.course.completed}) — consumed by notification-service.
 */
@Configuration
public class RabbitMQConfig {

    // Consumer: course-service events
    public static final String COURSE_EXCHANGE         = "lms.course.events";
    public static final String STUDENT_ENROLLED_KEY    = "lms.course.student.enrolled";
    public static final String PROGRESS_ENROLL_QUEUE   = "lms.progress.student-enrolled";

    // Dead-letter
    public static final String PROGRESS_DLQ            = "lms.progress.dlq";
    public static final String PROGRESS_DLQ_EXCHANGE    = "lms.progress.dlq.exchange";

    // Producer: progress events
    public static final String PROGRESS_EXCHANGE        = "lms.progress.events";
    public static final String COURSE_COMPLETED_KEY     = "lms.progress.course.completed";

    // ── Consumer topology ───────────────────────────────────────────────────

    @Bean
    public TopicExchange courseEventsExchange() {
        return ExchangeBuilder.topicExchange(COURSE_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue progressEnrollQueue() {
        return QueueBuilder.durable(PROGRESS_ENROLL_QUEUE)
                .withArgument("x-dead-letter-exchange", PROGRESS_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PROGRESS_DLQ)
                .withArgument("x-message-ttl", 300_000)   // 5-min TTL
                .build();
    }

    @Bean
    public Binding progressEnrollBinding() {
        return BindingBuilder.bind(progressEnrollQueue())
                .to(courseEventsExchange())
                .with(STUDENT_ENROLLED_KEY);
    }

    @Bean
    public DirectExchange progressDlqExchange() {
        return ExchangeBuilder.directExchange(PROGRESS_DLQ_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue progressDlQueue() {
        return QueueBuilder.durable(PROGRESS_DLQ).build();
    }

    @Bean
    public Binding progressDlqBinding() {
        return BindingBuilder.bind(progressDlQueue())
                .to(progressDlqExchange())
                .with(PROGRESS_DLQ);
    }

    // ── Producer topology ───────────────────────────────────────────────────

    @Bean
    public TopicExchange progressEventsExchange() {
        return ExchangeBuilder.topicExchange(PROGRESS_EXCHANGE).durable(true).build();
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
        template.setMandatory(true);
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
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
