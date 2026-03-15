package com.lms.userservice.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * User-service acts as an OAuth2 resource server.
 * It trusts the JWT already validated by the gateway (no re-validation needed
 * if the network is internal-only, but we validate anyway for defense in depth).
 *
 * <p>JWT validation uses the same JWKS endpoint from auth-service.
 * The jwk-set-uri is configured in the centralized config-server.
 *
 * <p>Role extraction: the "role" claim in the JWT is mapped to
 * Spring Security authorities with "ROLE_" prefix (Spring convention).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // GET /users/v1/{userId} — any authenticated user (can get own profile,
                // admins get any). Fine-grained ownership check is done in controller.
                .requestMatchers(HttpMethod.GET, "/users/v1/{userId}").authenticated()
                // Admin-only endpoints
                .requestMatchers("/users/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .build();
    }

    /**
     * Converts the "role" claim from JWT into Spring Security GrantedAuthority.
     * JWT claim: "role": "ADMIN"  →  Spring authority: "ROLE_ADMIN"
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null) return List.of();
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
