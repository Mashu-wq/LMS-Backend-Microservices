package com.lms.paymentservice.application.service;

import com.lms.paymentservice.application.dto.request.InitiatePaymentRequest;
import com.lms.paymentservice.application.dto.response.PageResponse;
import com.lms.paymentservice.application.dto.response.PaymentResponse;
import com.lms.paymentservice.application.port.PaymentEventPublisher;
import com.lms.paymentservice.application.port.PaymentGateway;
import com.lms.paymentservice.application.port.PaymentGateway.GatewayResult;
import com.lms.paymentservice.domain.exception.DuplicatePaymentException;
import com.lms.paymentservice.domain.exception.PaymentNotFoundException;
import com.lms.paymentservice.domain.model.Payment;
import com.lms.paymentservice.domain.model.PaymentStatus;
import com.lms.paymentservice.domain.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository    paymentRepository;
    private final PaymentGateway       paymentGateway;
    private final PaymentEventPublisher eventPublisher;

    private final Counter paymentsInitiatedCounter;
    private final Counter paymentsCompletedCounter;
    private final Counter paymentsFailedCounter;
    private final Counter refundsCompletedCounter;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentGateway paymentGateway,
                          PaymentEventPublisher eventPublisher,
                          MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway    = paymentGateway;
        this.eventPublisher    = eventPublisher;

        this.paymentsInitiatedCounter = Counter.builder("payment.initiated")
                .description("Total number of payment attempts initiated")
                .register(meterRegistry);
        this.paymentsCompletedCounter = Counter.builder("payment.completed")
                .description("Total number of payments completed successfully")
                .register(meterRegistry);
        this.paymentsFailedCounter = Counter.builder("payment.failed")
                .description("Total number of payments that failed")
                .register(meterRegistry);
        this.refundsCompletedCounter = Counter.builder("payment.refunded")
                .description("Total number of refunds processed")
                .register(meterRegistry);
    }

    // ── Initiate payment ────────────────────────────────────────────────────

    @Transactional
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "gatewayFallback")
    public PaymentResponse initiatePayment(UUID userId, InitiatePaymentRequest request) {
        String idempotencyKey = resolveIdempotencyKey(userId, request);

        // Idempotency check — return existing result for duplicate requests
        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent payment request for key={} — returning existing payment {}",
                    idempotencyKey, existing.get().getPaymentId());
            return PaymentResponse.from(existing.get());
        }

        // Guard against paying for the same course twice
        if (paymentRepository.existsByUserIdAndCourseIdAndStatus(
                userId, request.courseId(), PaymentStatus.COMPLETED)) {
            throw new DuplicatePaymentException(userId, request.courseId());
        }

        Payment payment = Payment.create(
                userId, request.courseId(),
                request.amount(), request.currency(),
                request.paymentMethod(), idempotencyKey);

        paymentRepository.save(payment);
        paymentsInitiatedCounter.increment();
        log.info("Payment {} initiated — userId={} courseId={} amount={} {}",
                payment.getPaymentId(), userId, request.courseId(),
                request.amount(), request.currency());

        // Call payment gateway (wrapped in circuit breaker)
        payment.markProcessing();
        GatewayResult result = paymentGateway.charge(payment);

        if (result.success()) {
            payment.complete(result.transactionId());
            paymentRepository.save(payment);
            paymentsCompletedCounter.increment();
            eventPublisher.publishPaymentCompleted(payment);
            log.info("Payment {} completed — txId={}", payment.getPaymentId(), result.transactionId());
        } else {
            payment.fail(result.errorMessage());
            paymentRepository.save(payment);
            paymentsFailedCounter.increment();
            eventPublisher.publishPaymentFailed(payment);
            log.warn("Payment {} failed — reason={}", payment.getPaymentId(), result.errorMessage());
        }

        return PaymentResponse.from(payment);
    }

    /**
     * Circuit breaker opens when the payment gateway is repeatedly unavailable.
     * We persist the payment in PENDING state and return it — the client can
     * poll until it transitions to COMPLETED or FAILED.
     */
    public PaymentResponse gatewayFallback(UUID userId, InitiatePaymentRequest request,
                                            Throwable cause) {
        log.error("Payment gateway circuit breaker open for userId={}: {}", userId, cause.getMessage());
        String idempotencyKey = resolveIdempotencyKey(userId, request);
        Payment pending = Payment.create(
                userId, request.courseId(),
                request.amount(), request.currency(),
                request.paymentMethod(), idempotencyKey);
        paymentRepository.save(pending);
        paymentsInitiatedCounter.increment();
        return PaymentResponse.from(pending);
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    @Cacheable(value = "payments", key = "#paymentId")
    public PaymentResponse getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    public PageResponse<PaymentResponse> getUserPayments(UUID userId, Pageable pageable) {
        return PageResponse.from(
                paymentRepository.findByUserId(userId, pageable),
                PaymentResponse::from);
    }

    public PageResponse<PaymentResponse> getAllPayments(PaymentStatus status, Pageable pageable) {
        var page = (status != null)
                ? paymentRepository.findByStatus(status, pageable)
                : paymentRepository.findAll(pageable);
        return PageResponse.from(page, PaymentResponse::from);
    }

    // ── Refund (admin) ──────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "payments", key = "#paymentId")
    @CircuitBreaker(name = "paymentGateway")
    public PaymentResponse refundPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        payment.requestRefund();
        paymentRepository.save(payment);

        GatewayResult result = paymentGateway.refund(payment);

        if (result.success()) {
            payment.completeRefund();
            paymentRepository.save(payment);
            refundsCompletedCounter.increment();
            eventPublisher.publishRefundCompleted(payment);
            log.info("Refund completed for payment {} — txId={}", paymentId, result.transactionId());
        } else {
            log.error("Refund failed for payment {} — reason={}", paymentId, result.errorMessage());
            // Leave in REFUND_PENDING for a retry job; do not revert to COMPLETED
        }

        return PaymentResponse.from(payment);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String resolveIdempotencyKey(UUID userId, InitiatePaymentRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            return request.idempotencyKey();
        }
        // Deterministic fallback: one PENDING/COMPLETED payment per user+course
        return userId + ":" + request.courseId();
    }
}
