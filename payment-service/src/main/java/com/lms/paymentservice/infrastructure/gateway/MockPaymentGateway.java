package com.lms.paymentservice.infrastructure.gateway;

import com.lms.paymentservice.application.port.PaymentGateway;
import com.lms.paymentservice.domain.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated payment gateway for local development and portfolio demonstration.
 *
 * <p>In a production system this adapter would wrap a real SDK (e.g. Stripe, PayPal).
 * The {@code PaymentGateway} port keeps the application layer completely decoupled
 * from the provider — swapping to a real implementation requires zero changes outside
 * this class.
 *
 * <p>Configurable failure rate via {@code payment.gateway.simulate-failure-rate}
 * (defaults to 5%) to exercise the FAILED path in tests.
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Value("${payment.gateway.simulate-failure-rate:0.05}")
    private double failureRate;

    @Value("${payment.gateway.processing-delay-ms:500}")
    private long processingDelayMs;

    @Override
    public GatewayResult charge(Payment payment) {
        log.info("MockPaymentGateway: charging {} {} for payment {}",
                payment.getAmount(), payment.getCurrency(), payment.getPaymentId());

        simulateProcessingDelay();

        if (shouldSimulateFailure()) {
            log.warn("MockPaymentGateway: simulated failure for payment {}", payment.getPaymentId());
            return GatewayResult.failure("Simulated gateway decline — insufficient funds");
        }

        String txId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("MockPaymentGateway: charge successful txId={}", txId);
        return GatewayResult.success(txId);
    }

    @Override
    public GatewayResult refund(Payment payment) {
        log.info("MockPaymentGateway: refunding payment {} txId={}",
                payment.getPaymentId(), payment.getTransactionId());

        simulateProcessingDelay();

        if (shouldSimulateFailure()) {
            log.warn("MockPaymentGateway: simulated refund failure for payment {}", payment.getPaymentId());
            return GatewayResult.failure("Simulated refund failure — gateway timeout");
        }

        String refundTxId = "REF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("MockPaymentGateway: refund successful refundTxId={}", refundTxId);
        return GatewayResult.success(refundTxId);
    }

    private boolean shouldSimulateFailure() {
        return ThreadLocalRandom.current().nextDouble() < failureRate;
    }

    private void simulateProcessingDelay() {
        if (processingDelayMs > 0) {
            try {
                Thread.sleep(processingDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
