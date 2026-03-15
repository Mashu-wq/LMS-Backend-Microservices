package com.lms.eurekaserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Eureka dashboard is protected behind HTTP Basic auth.
 * Eureka clients must provide credentials in their service-url.
 * CSRF is disabled because the Eureka client REST calls don't carry CSRF tokens.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                // Disable CSRF for Eureka REST endpoints used by clients
                .ignoringRequestMatchers("/eureka/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {});

        return http.build();
    }
}
