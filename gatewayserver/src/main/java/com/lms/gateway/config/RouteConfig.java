package com.lms.gateway.config;

import com.lms.gateway.filter.RequestLoggingFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Gateway Route Definitions.
 *
 * <p>Architectural decision: explicit route definitions (not discovery locator)
 * give us full control over circuit breakers, rate limiters, and path rewriting
 * per service. Discovery locator auto-routing loses that granularity.
 *
 * <p>Pattern: all routes use lb:// (load-balanced via Eureka) so we get
 * client-side load balancing for free when scaling services horizontally.
 */
@Configuration
public class RouteConfig {

    private final RequestLoggingFilter requestLoggingFilter;

    public RouteConfig(RequestLoggingFilter requestLoggingFilter) {
        this.requestLoggingFilter = requestLoggingFilter;
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()

            // ── AUTH SERVICE ─────────────────────────────────────────────────
            .route("auth-service", r -> r
                .path("/auth/v1/**")
                .filters(f -> f
                    .filter(requestLoggingFilter)
                    .circuitBreaker(c -> c
                        .setName("authServiceCB")
                        .setFallbackUri("forward:/fallback/auth")
                    )
                    .retry(config -> config
                        .setRetries(2)
                        .setMethods(org.springframework.http.HttpMethod.GET)
                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(1000), 2, true)
                    )
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(ipKeyResolver())
                    )
                )
                .uri("lb://auth-service")
            )

            // ── USER SERVICE ─────────────────────────────────────────────────
            .route("user-service", r -> r
                .path("/users/v1/**")
                .filters(f -> f
                    .filter(requestLoggingFilter)
                    .circuitBreaker(c -> c
                        .setName("userServiceCB")
                        .setFallbackUri("forward:/fallback/user")
                    )
                    .retry(config -> config
                        .setRetries(2)
                        .setMethods(org.springframework.http.HttpMethod.GET)
                    )
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(ipKeyResolver())
                    )
                )
                .uri("lb://user-service")
            )

            // ── COURSE SERVICE ───────────────────────────────────────────────
            .route("course-service", r -> r
                .path("/courses/v1/**")
                .filters(f -> f
                    .filter(requestLoggingFilter)
                    .circuitBreaker(c -> c
                        .setName("courseServiceCB")
                        .setFallbackUri("forward:/fallback/course")
                    )
                    .retry(config -> config
                        .setRetries(2)
                        .setMethods(org.springframework.http.HttpMethod.GET)
                    )
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(ipKeyResolver())
                    )
                )
                .uri("lb://course-service")
            )

            // ── PROGRESS SERVICE ─────────────────────────────────────────────
            .route("progress-service", r -> r
                .path("/progress/v1/**")
                .filters(f -> f
                    .filter(requestLoggingFilter)
                    .circuitBreaker(c -> c
                        .setName("progressServiceCB")
                        .setFallbackUri("forward:/fallback/progress")
                    )
                )
                .uri("lb://progress-service")
            )

            // ── PAYMENT SERVICE ──────────────────────────────────────────────
            .route("payment-service", r -> r
                .path("/payments/v1/**")
                .filters(f -> f
                    .filter(requestLoggingFilter)
                    .circuitBreaker(c -> c
                        .setName("paymentServiceCB")
                        .setFallbackUri("forward:/fallback/payment")
                    )
                    // No retry on payment — idempotency must be handled by client
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(userKeyResolver())
                    )
                )
                .uri("lb://payment-service")
            )

            // ── SEARCH SERVICE ───────────────────────────────────────────────
            .route("search-service", r -> r
                .path("/search/v1/**")
                .filters(f -> f
                    .filter(requestLoggingFilter)
                    .circuitBreaker(c -> c
                        .setName("searchServiceCB")
                        .setFallbackUri("forward:/fallback/search")
                    )
                    .retry(config -> config.setRetries(3))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(searchRateLimiter())
                        .setKeyResolver(ipKeyResolver())
                    )
                )
                .uri("lb://search-service")
            )

            .build();
    }

    @Primary
    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter redisRateLimiter() {
        // 20 requests per second, burst up to 40
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(20, 40, 1);
    }

    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter searchRateLimiter() {
        // Search gets higher limits — read-only, cacheable
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(50, 100, 1);
    }

    @Primary
    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.KeyResolver ipKeyResolver() {
        return exchange -> reactor.core.publisher.Mono.justOrEmpty(
            exchange.getRequest().getRemoteAddress()
        ).map(addr -> addr.getAddress().getHostAddress());
    }

    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.KeyResolver userKeyResolver() {
        // Rate limit payment by authenticated user (sub claim from JWT)
        return exchange -> exchange.getPrincipal()
            .map(java.security.Principal::getName)
            .defaultIfEmpty("anonymous");
    }
}
