package com.lms.courseservice.infrastructure.messaging;

import com.lms.courseservice.application.service.EnrollmentService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumes PaymentCompletedEvent from payment-service.
 * On successful payment, triggers student enrollment in the purchased course.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventConsumer {

    private final EnrollmentService enrollmentService;

    @RabbitListener(queues = RabbitMQConfig.COURSE_PAYMENT_QUEUE)
    public void handlePaymentCompleted(PaymentCompletedEvent event,
                                        Channel channel,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("Received PaymentCompletedEvent paymentId={} courseId={} studentId={}",
                event.paymentId(), event.courseId(), event.studentId());
        try {
            enrollmentService.enroll(event.courseId(), event.studentId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to process PaymentCompletedEvent paymentId={}: {}",
                    event.paymentId(), e.getMessage(), e);
            boolean requeue = isTransientError(e);
            channel.basicNack(deliveryTag, false, requeue);
        }
    }

    private boolean isTransientError(Exception e) {
        return e instanceof org.springframework.dao.TransientDataAccessException;
    }

    // Local copy of the payment event contract
    public record PaymentCompletedEvent(
            UUID eventId, UUID paymentId, String courseId,
            UUID studentId, BigDecimal amount, Instant occurredAt) {}
}
