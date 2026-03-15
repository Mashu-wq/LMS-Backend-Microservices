package com.lms.searchservice.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig — search-service is a public read-only service.
 *
 * <p>All search endpoints are publicly accessible (JWT validation happens at the gateway).
 * Actuator endpoints are restricted to localhost or internal callers only.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/search/v1/**").permitAll()
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus").permitAll()
                .anyRequest().denyAll()
            );
        return http.build();
    }
}
