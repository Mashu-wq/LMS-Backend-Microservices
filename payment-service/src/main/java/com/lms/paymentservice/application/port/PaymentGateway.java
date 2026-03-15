package com.lms.paymentservice.application.port;

import com.lms.paymentservice.domain.model.Payment;

/**
 * Port for the external payment gateway.
 *
 * <p>The application layer depends on this interface, not on any concrete
 * gateway SDK. The mock implementation lives in infrastructure.gateway and
 * can be replaced with a real Stripe/PayPal adapter without touching the domain.
 */
public interface PaymentGateway {

    GatewayResult charge(Payment payment);

    GatewayResult refund(Payment payment);

    record GatewayResult(boolean success, String transactionId, String errorMessage) {

        public static GatewayResult success(String transactionId) {
            return new GatewayResult(true, transactionId, null);
        }

        public static GatewayResult failure(String errorMessage) {
            return new GatewayResult(false, null, errorMessage);
        }
    }
}
