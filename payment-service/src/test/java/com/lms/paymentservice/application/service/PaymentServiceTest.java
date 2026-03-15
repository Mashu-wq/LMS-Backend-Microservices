package com.lms.paymentservice.application.service;

import com.lms.paymentservice.application.dto.request.InitiatePaymentRequest;
import com.lms.paymentservice.application.dto.response.PaymentResponse;
import com.lms.paymentservice.application.port.PaymentEventPublisher;
import com.lms.paymentservice.application.port.PaymentGateway;
import com.lms.paymentservice.application.port.PaymentGateway.GatewayResult;
import com.lms.paymentservice.domain.exception.DuplicatePaymentException;
import com.lms.paymentservice.domain.exception.PaymentNotFoundException;
import com.lms.paymentservice.domain.model.Payment;
import com.lms.paymentservice.domain.model.PaymentMethod;
import com.lms.paymentservice.domain.model.PaymentStatus;
import com.lms.paymentservice.domain.repository.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository    paymentRepository;
    @Mock private PaymentGateway       paymentGateway;
    @Mock private PaymentEventPublisher eventPublisher;

    private PaymentService paymentService;

    private final UUID userId   = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, paymentGateway, eventPublisher,
                new SimpleMeterRegistry());
    }

    // ── initiatePayment ─────────────────────────────────────────────────────

    @Test
    @DisplayName("initiatePayment: gateway success → COMPLETED, event published")
    void initiatePayment_success() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                courseId, new BigDecimal("49.99"), "USD",
                PaymentMethod.CREDIT_CARD, null);

        given(paymentRepository.findByIdempotencyKey(anyString())).willReturn(Optional.empty());
        given(paymentRepository.existsByUserIdAndCourseIdAndStatus(
                userId, courseId, PaymentStatus.COMPLETED)).willReturn(false);
        given(paymentRepository.save(any(Payment.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(paymentGateway.charge(any(Payment.class)))
                .willReturn(GatewayResult.success("TXN-ABC123"));

        PaymentResponse response = paymentService.initiatePayment(userId, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.transactionId()).isEqualTo("TXN-ABC123");
        verify(eventPublisher).publishPaymentCompleted(any(Payment.class));
        verify(eventPublisher, never()).publishPaymentFailed(any());
    }

    @Test
    @DisplayName("initiatePayment: gateway failure → FAILED, failure event published")
    void initiatePayment_gatewayFailure() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                courseId, new BigDecimal("49.99"), "USD",
                PaymentMethod.CREDIT_CARD, null);

        given(paymentRepository.findByIdempotencyKey(anyString())).willReturn(Optional.empty());
        given(paymentRepository.existsByUserIdAndCourseIdAndStatus(
                userId, courseId, PaymentStatus.COMPLETED)).willReturn(false);
        given(paymentRepository.save(any(Payment.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(paymentGateway.charge(any(Payment.class)))
                .willReturn(GatewayResult.failure("Insufficient funds"));

        PaymentResponse response = paymentService.initiatePayment(userId, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Insufficient funds");
        verify(eventPublisher).publishPaymentFailed(any(Payment.class));
        verify(eventPublisher, never()).publishPaymentCompleted(any());
    }

    @Test
    @DisplayName("initiatePayment: idempotent — returns existing payment without re-charging")
    void initiatePayment_idempotent() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                courseId, new BigDecimal("49.99"), "USD",
                PaymentMethod.CREDIT_CARD, "my-key-123");

        Payment existing = Payment.create(userId, courseId,
                new BigDecimal("49.99"), "USD",
                PaymentMethod.CREDIT_CARD, "my-key-123");
        given(paymentRepository.findByIdempotencyKey("my-key-123"))
                .willReturn(Optional.of(existing));

        PaymentResponse response = paymentService.initiatePayment(userId, request);

        assertThat(response.paymentId()).isEqualTo(existing.getPaymentId());
        verify(paymentGateway, never()).charge(any());
        verify(eventPublisher, never()).publishPaymentCompleted(any());
    }

    @Test
    @DisplayName("initiatePayment: duplicate payment for same course → DuplicatePaymentException")
    void initiatePayment_duplicateCourse() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                courseId, new BigDecimal("49.99"), "USD",
                PaymentMethod.CREDIT_CARD, null);

        given(paymentRepository.findByIdempotencyKey(anyString())).willReturn(Optional.empty());
        given(paymentRepository.existsByUserIdAndCourseIdAndStatus(
                userId, courseId, PaymentStatus.COMPLETED)).willReturn(true);

        assertThatThrownBy(() -> paymentService.initiatePayment(userId, request))
                .isInstanceOf(DuplicatePaymentException.class);

        verify(paymentGateway, never()).charge(any());
    }

    // ── getPayment ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPayment: found → returns response")
    void getPayment_found() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.create(userId, courseId,
                new BigDecimal("49.99"), "USD",
                PaymentMethod.PAYPAL, "key");
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment(paymentId);

        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("getPayment: not found → PaymentNotFoundException")
    void getPayment_notFound() {
        UUID paymentId = UUID.randomUUID();
        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(paymentId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ── refundPayment ────────────────────────────────────────────────────────

    @Test
    @DisplayName("refundPayment: completed payment → REFUNDED, event published")
    void refundPayment_success() {
        UUID paymentId = UUID.randomUUID();
        // Build a payment already in COMPLETED state
        Payment payment = Payment.reconstitute(
                paymentId, userId, courseId,
                new BigDecimal("49.99"), "USD",
                PaymentStatus.COMPLETED, PaymentMethod.CREDIT_CARD,
                "TXN-123", null, "key",
                java.time.Instant.now(), java.time.Instant.now(), java.time.Instant.now());

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(paymentGateway.refund(any(Payment.class)))
                .willReturn(GatewayResult.success("REF-XYZ"));

        PaymentResponse response = paymentService.refundPayment(paymentId);

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        verify(eventPublisher).publishRefundCompleted(any(Payment.class));
    }

    @Test
    @DisplayName("refundPayment: gateway failure → stays REFUND_PENDING, no event published")
    void refundPayment_gatewayFailure_staysRefundPending() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.reconstitute(
                paymentId, userId, courseId,
                new BigDecimal("49.99"), "USD",
                PaymentStatus.COMPLETED, PaymentMethod.CREDIT_CARD,
                "TXN-123", null, "key",
                java.time.Instant.now(), java.time.Instant.now(), java.time.Instant.now());

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(paymentGateway.refund(any(Payment.class)))
                .willReturn(GatewayResult.failure("Gateway timeout"));

        PaymentResponse response = paymentService.refundPayment(paymentId);

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUND_PENDING);
        verify(eventPublisher, never()).publishRefundCompleted(any());
    }
}
