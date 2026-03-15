package com.lms.paymentservice.infrastructure.messaging;

import com.lms.paymentservice.application.port.PaymentEventPublisher;
import com.lms.paymentservice.domain.model.Payment;
import com.lms.paymentservice.infrastructure.messaging.event.PaymentCompletedEvent;
import com.lms.paymentservice.infrastructure.messaging.event.PaymentFailedEvent;
import com.lms.paymentservice.infrastructure.messaging.event.RefundCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisherImpl implements PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishPaymentCompleted(Payment payment) {
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
                payment.getPaymentId(),
                payment.getUserId(),
                payment.getCourseId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getTransactionId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_COMPLETED_KEY,
                event);

        log.debug("Published PaymentCompletedEvent eventId={} paymentId={}",
                event.eventId(), payment.getPaymentId());
    }

    @Override
    public void publishPaymentFailed(Payment payment) {
        PaymentFailedEvent event = PaymentFailedEvent.of(
                payment.getPaymentId(),
                payment.getUserId(),
                payment.getCourseId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getFailureReason());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_FAILED_KEY,
                event);

        log.debug("Published PaymentFailedEvent eventId={} paymentId={}",
                event.eventId(), payment.getPaymentId());
    }

    @Override
    public void publishRefundCompleted(Payment payment) {
        RefundCompletedEvent event = RefundCompletedEvent.of(
                payment.getPaymentId(),
                payment.getUserId(),
                payment.getCourseId(),
                payment.getAmount(),
                payment.getCurrency());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.REFUND_COMPLETED_KEY,
                event);

        log.debug("Published RefundCompletedEvent eventId={} paymentId={}",
                event.eventId(), payment.getPaymentId());
    }
}
