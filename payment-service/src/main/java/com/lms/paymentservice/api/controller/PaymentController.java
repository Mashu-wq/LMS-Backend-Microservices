package com.lms.paymentservice.api.controller;

import com.lms.paymentservice.application.dto.request.InitiatePaymentRequest;
import com.lms.paymentservice.application.dto.response.PageResponse;
import com.lms.paymentservice.application.dto.response.PaymentResponse;
import com.lms.paymentservice.application.service.PaymentService;
import com.lms.paymentservice.domain.model.PaymentStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/payments/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Initiate a payment for a course purchase.
     * The response will contain a COMPLETED or FAILED status synchronously
     * (mock gateway responds immediately). In a real system this would be
     * async with a webhook callback.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InitiatePaymentRequest request) {

        UUID userId = UUID.fromString(jwt.getSubject());
        PaymentResponse response = paymentService.initiatePayment(userId, request);
        URI location = URI.create("/payments/v1/" + response.paymentId());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Get a specific payment by ID.
     * Users may only see their own payments; admins may see any.
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId) {

        UUID userId   = UUID.fromString(jwt.getSubject());
        String role   = jwt.getClaimAsString("role");
        PaymentResponse payment = paymentService.getPayment(paymentId);

        if (!"ADMIN".equals(role) && !payment.userId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(payment);
    }

    /**
     * List the authenticated user's own payment history.
     */
    @GetMapping("/my-payments")
    public ResponseEntity<PageResponse<PaymentResponse>> getMyPayments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId  = UUID.fromString(jwt.getSubject());
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getUserPayments(userId, pageable));
    }

    /**
     * Admin: process a refund for a completed payment.
     */
    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.refundPayment(paymentId));
    }

    /**
     * Admin: list all payments with optional status filter.
     */
    @GetMapping("/admin/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<PaymentResponse>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getAllPayments(status, pageable));
    }

    /**
     * Internal endpoint — not exposed through the gateway, used by Feign clients
     * within the cluster (e.g., progress-service verifying payment before
     * granting course access).
     */
    @GetMapping("/internal/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentInternal(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }
}
