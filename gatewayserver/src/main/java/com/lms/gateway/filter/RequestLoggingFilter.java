package com.lms.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Global request/response logging filter for the gateway.
 *
 * <p>Logs: method, path, status, duration, correlation ID (from trace context).
 * Does NOT log request/response bodies to avoid sensitive data exposure.
 * Body logging should only happen in debug environments and behind feature flags.
 *
 * <p>Implements {@link Ordered} with lowest priority so it wraps all other filters.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GatewayFilter, Ordered {

    private static final String START_TIME_ATTR = "requestStartTime";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Inject correlation ID — use existing or generate new
        String correlationId = request.getHeaders().getFirst(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;

        // Mutate request to propagate correlation ID downstream
        ServerHttpRequest mutatedRequest = request.mutate()
            .header(CORRELATION_HEADER, finalCorrelationId)
            .build();

        exchange.getAttributes().put(START_TIME_ATTR, Instant.now().toEpochMilli());

        log.info("GATEWAY_IN method={} path={} correlationId={}",
            request.getMethod(),
            request.getPath().value(),
            finalCorrelationId
        );

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
            .doFinally(signalType -> {
                ServerHttpResponse response = exchange.getResponse();
                long startTime = (long) exchange.getAttributes().getOrDefault(START_TIME_ATTR, 0L);
                long durationMs = Instant.now().toEpochMilli() - startTime;

                log.info("GATEWAY_OUT method={} path={} status={} durationMs={} correlationId={}",
                    request.getMethod(),
                    request.getPath().value(),
                    response.getStatusCode() != null ? response.getStatusCode().value() : "UNKNOWN",
                    durationMs,
                    finalCorrelationId
                );
            });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
