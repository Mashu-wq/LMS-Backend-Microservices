package com.lms.notificationservice.infrastructure.messaging;

import com.lms.notificationservice.application.dto.EmailMessage;
import com.lms.notificationservice.application.service.EmailService;
import com.lms.notificationservice.infrastructure.feign.CourseServiceFeignClient;
import com.lms.notificationservice.infrastructure.feign.UserServiceFeignClient;
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
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedEventConsumer {

    private final EmailService             emailService;
    private final UserServiceFeignClient   userClient;
    private final CourseServiceFeignClient courseClient;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_FAILED_QUEUE)
    public void handlePaymentFailed(PaymentFailedEvent event,
                                     Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.info("Sending payment failed email paymentId={} userId={}",
                event.paymentId(), event.userId());
        try {
            var user   = userClient.getUserProfile(event.userId());
            var course = courseClient.getCourseInfo(event.courseId().toString());

            if (user.email() == null) {
                log.warn("No email for userId={} — skipping payment failed notification", event.userId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            emailService.send(new EmailMessage(
                    user.email(),
                    "Action required: payment failed for " + course.title(),
                    "payment-failed",
                    Map.of(
                            "courseName",    course.title(),
                            "amount",        event.amount(),
                            "currency",      event.currency(),
                            "failureReason", event.failureReason() != null
                                    ? event.failureReason() : "Unknown reason",
                            "attemptDate",   event.occurredAt()
                    )
            ));
            channel.basicAck(deliveryTag, false);
        } catch (EmailService.EmailDeliveryException e) {
            log.error("SMTP failure for payment-failed email paymentId={}: {}",
                    event.paymentId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        } catch (Exception e) {
            log.error("Permanent error processing PaymentFailedEvent paymentId={}: {}",
                    event.paymentId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    public record PaymentFailedEvent(
            UUID       eventId,
            UUID       paymentId,
            UUID       userId,
            UUID       courseId,
            BigDecimal amount,
            String     currency,
            String     failureReason,
            Instant    occurredAt
    ) {}
}
