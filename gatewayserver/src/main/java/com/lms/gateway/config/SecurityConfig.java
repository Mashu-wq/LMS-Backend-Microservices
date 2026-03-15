package com.lms.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway Security Configuration.
 *
 * <p>The gateway acts as an OAuth2 Resource Server — it validates JWT tokens
 * using the public JWKS endpoint exposed by auth-service. Downstream services
 * trust the gateway and do NOT re-validate tokens (defense-in-depth optional).
 *
 * <p>Public routes (login, register, health, docs) bypass JWT validation.
 * All other routes require a valid Bearer token.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Infrastructure
                .pathMatchers("/actuator/**").permitAll()
                // Auth endpoints — public
                .pathMatchers("/auth/v1/login", "/auth/v1/register",
                              "/auth/v1/refresh", "/auth/v1/.well-known/**").permitAll()
                // Public course browsing
                .pathMatchers(HttpMethod.GET, "/courses/v1/**").permitAll()
                // Search is public
                .pathMatchers(HttpMethod.GET, "/search/v1/**").permitAll()
                // Everything else requires authentication
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
            )
            .build();
    }
}
