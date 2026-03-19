package com.lms.authservice.infrastructure.security.config;

import com.lms.authservice.application.port.PasswordEncoder;
import com.lms.authservice.infrastructure.security.jwt.RsaKeyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * Security configuration for auth-service.
 *
 * <p>Auth-service is NOT an OAuth2 resource server — it IS the authorization server.
 * It generates and validates its own tokens via JwtTokenService.
 * Endpoints are mostly public; only admin endpoints are protected.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final RsaKeyProperties rsaKeyProperties;

    public SecurityConfig(RsaKeyProperties rsaKeyProperties) {
        this.rsaKeyProperties = rsaKeyProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/v1/register",
                    "/auth/v1/login",
                    "/auth/v1/refresh",
                    "/auth/v1/logout",
                    "/auth/v1/.well-known/**",
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/prometheus"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .build();
    }

    /**
     * RSA key pair for JWT signing.
     *
     * <p>Production: inject RSA_PRIVATE_KEY and RSA_PUBLIC_KEY env vars (Base64 PEM).
     * Local dev: auto-generates a new key pair each startup (tokens won't survive restarts).
     *
     * <p>WARNING: In production, use a stable key from a secrets manager.
     * Rotating keys requires updating the JWKS endpoint and giving clients time to re-fetch.
     */
    @Bean
    public KeyPair rsaKeyPair() throws NoSuchAlgorithmException {
        if (rsaKeyProperties.privateKey() != null && !rsaKeyProperties.privateKey().isBlank()) {
            return loadKeyPairFromPem();
        }
        // Auto-generate for local development
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private KeyPair loadKeyPairFromPem() {
        try {
            byte[] privateBytes = Base64.getDecoder().decode(sanitizePem(rsaKeyProperties.privateKey()));
            byte[] publicBytes = Base64.getDecoder().decode(sanitizePem(rsaKeyProperties.publicKey()));

            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(
                    new java.security.spec.PKCS8EncodedKeySpec(privateBytes));
            RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(publicBytes));

            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA key pair from configuration", e);
        }
    }

    private String sanitizePem(String pem) {
        return pem.replace("-----BEGIN PRIVATE KEY-----", "")
                  .replace("-----END PRIVATE KEY-----", "")
                  .replace("-----BEGIN PUBLIC KEY-----", "")
                  .replace("-----END PUBLIC KEY-----", "")
                  .replaceAll("\\s+", "");
    }

    /**
     * PasswordEncoder port adapter — wraps Spring's BCrypt.
     * Exposed as a @Bean so the application service can inject the port interface,
     * not the BCrypt class.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        return new PasswordEncoder() {
            @Override
            public String encode(String rawPassword) {
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(String rawPassword, String encodedPassword) {
                return bcrypt.matches(rawPassword, encodedPassword);
            }
        };
    }
}
