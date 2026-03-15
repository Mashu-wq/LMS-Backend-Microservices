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
public class PaymentCompletedEventConsumer {

    private final EmailService           emailService;
    private final UserServiceFeignClient userClient;
    private final CourseServiceFeignClient courseClient;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    public void handlePaymentCompleted(PaymentCompletedEvent event,
                                        Channel channel,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.info("Sending payment confirmation email paymentId={} userId={}",
                event.paymentId(), event.userId());
        try {
            var user   = userClient.getUserProfile(event.userId());
            var course = courseClient.getCourseInfo(event.courseId().toString());

            if (user.email() == null) {
                log.warn("No email for userId={} — skipping payment confirmation", event.userId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            emailService.send(new EmailMessage(
                    user.email(),
                    "Your payment receipt – " + course.title(),
                    "payment-confirmation",
                    Map.of(
                            "courseName",    course.title(),
                            "transactionId", event.transactionId(),
                            "paymentMethod", formatMethod(event.paymentMethod()),
                            "paymentDate",   event.occurredAt(),
                            "amount",        event.amount(),
                            "currency",      event.currency()
                    )
            ));
            channel.basicAck(deliveryTag, false);
        } catch (EmailService.EmailDeliveryException e) {
            log.error("SMTP failure for payment confirmation paymentId={}: {}",
                    event.paymentId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        } catch (Exception e) {
            log.error("Permanent error processing PaymentCompletedEvent paymentId={}: {}",
                    event.paymentId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private String formatMethod(String method) {
        if (method == null) return "Card";
        String s = method.replace("_", " ").toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public record PaymentCompletedEvent(
            UUID       eventId,
            UUID       paymentId,
            UUID       userId,
            UUID       courseId,
            BigDecimal amount,
            String     currency,
            String     transactionId,
            String     paymentMethod,
            Instant    occurredAt
    ) {}
}
