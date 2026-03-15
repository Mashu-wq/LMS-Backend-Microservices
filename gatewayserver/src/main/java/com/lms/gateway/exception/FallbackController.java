package com.lms.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.Map;

/**
 * Circuit breaker fallback controller.
 *
 * <p>When a downstream service's circuit breaker is OPEN, the gateway forwards
 * the request here instead of letting it fail with a 500. The response is a
 * structured error body that clients can parse gracefully.
 *
 * <p>Each fallback includes a "retryAfter" hint so clients can implement backoff.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback(ServerWebExchange exchange) {
        return buildFallback("auth-service", "Authentication service is temporarily unavailable", exchange);
    }

    @RequestMapping("/user")
    public ResponseEntity<Map<String, Object>> userFallback(ServerWebExchange exchange) {
        return buildFallback("user-service", "User service is temporarily unavailable", exchange);
    }

    @RequestMapping("/course")
    public ResponseEntity<Map<String, Object>> courseFallback(ServerWebExchange exchange) {
        return buildFallback("course-service", "Course service is temporarily unavailable", exchange);
    }

    @RequestMapping("/progress")
    public ResponseEntity<Map<String, Object>> progressFallback(ServerWebExchange exchange) {
        return buildFallback("progress-service", "Progress service is temporarily unavailable", exchange);
    }

    @RequestMapping("/payment")
    public ResponseEntity<Map<String, Object>> paymentFallback(ServerWebExchange exchange) {
        return buildFallback("payment-service", "Payment service is temporarily unavailable. Please retry your request", exchange);
    }

    @RequestMapping("/search")
    public ResponseEntity<Map<String, Object>> searchFallback(ServerWebExchange exchange) {
        return buildFallback("search-service", "Search service is temporarily unavailable", exchange);
    }

    private ResponseEntity<Map<String, Object>> buildFallback(
            String service, String message, ServerWebExchange exchange) {

        String correlationId = exchange.getRequest().getHeaders()
            .getFirst("X-Correlation-Id");

        log.warn("CIRCUIT_BREAKER_OPEN service={} correlationId={}", service, correlationId);

        Map<String, Object> body = Map.of(
            "timestamp", Instant.now().toString(),
            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
            "error", "Service Unavailable",
            "message", message,
            "service", service,
            "correlationId", correlationId != null ? correlationId : "unknown",
            "retryAfter", 30
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }
}
