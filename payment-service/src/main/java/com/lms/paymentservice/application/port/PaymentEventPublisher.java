package com.lms.paymentservice.application.port;

import com.lms.paymentservice.domain.model.Payment;

public interface PaymentEventPublisher {

    void publishPaymentCompleted(Payment payment);

    void publishPaymentFailed(Payment payment);

    void publishRefundCompleted(Payment payment);
}
