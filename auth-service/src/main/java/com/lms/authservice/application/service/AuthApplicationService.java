package com.lms.authservice.application.service;

import com.lms.authservice.application.dto.request.LoginRequest;
import com.lms.authservice.application.dto.request.RefreshTokenRequest;
import com.lms.authservice.application.dto.request.RegisterRequest;
import com.lms.authservice.application.dto.response.AuthResponse;
import com.lms.authservice.application.dto.response.RegisterResponse;
import com.lms.authservice.application.port.EventPublisher;
import com.lms.authservice.application.port.PasswordEncoder;
import com.lms.authservice.application.port.TokenService;
import com.lms.authservice.domain.event.UserRegisteredEvent;
import com.lms.authservice.domain.model.RefreshToken;
import com.lms.authservice.domain.model.User;
import com.lms.authservice.domain.repository.RefreshTokenRepository;
import com.lms.authservice.domain.repository.UserRepository;
import com.lms.authservice.infrastructure.security.exception.AccountLockedException;
import com.lms.authservice.infrastructure.security.exception.InvalidCredentialsException;
import com.lms.authservice.infrastructure.security.exception.InvalidTokenException;
import com.lms.authservice.infrastructure.security.exception.UserAlreadyExistsException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.authservice.domain.model.OutboxEvent;
import com.lms.authservice.domain.repository.OutboxRepository;

/**
 * Auth Application Service — orchestrates the core use cases.
 *
 * <p>This class coordinates domain objects and ports but contains NO
 * business logic itself. Business rules live in the domain model.
 * Infrastructure concerns (JWT, BCrypt, RabbitMQ) are accessed via ports.
 *
 * <p>All methods are @Transactional to ensure atomicity across
 * repository calls within a single use case.
 */
@Slf4j
@Service
@Transactional
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // Metrics
    private final Counter registrationCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;

    public AuthApplicationService( UserRepository userRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   TokenService tokenService,
                                   PasswordEncoder passwordEncoder,
                                   EventPublisher eventPublisher,
                                   OutboxRepository outboxRepository,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;

        this.registrationCounter = Counter.builder("auth.registrations.total")
                .description("Total user registrations")
                .register(meterRegistry);
        this.loginSuccessCounter = Counter.builder("auth.login.success.total")
                .description("Total successful logins")
                .register(meterRegistry);
        this.loginFailureCounter = Counter.builder("auth.login.failure.total")
                .description("Total failed login attempts")
                .register(meterRegistry);
    }

    // ── Use Case: Register ───────────────────────────────────

    public RegisterResponse register(RegisterRequest request) {
        log.info("Registration attempt for email={}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(
                    "An account with email '%s' already exists".formatted(request.email()));
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = User.create(
                request.email(),
                passwordHash,
                request.firstName(),
                request.lastName(),
                request.role()
        );

        User savedUser = userRepository.save(user);

        // Publish event — notification-service sends welcome email,
        // user-service creates the profile record
        UserRegisteredEvent event = UserRegisteredEvent.of(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getRole().name()
        );

        try {

            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.create(
                    "User",
                    savedUser.getId(),
                    "UserRegisteredEvent",
                    payload
            );

            outboxRepository.save(outboxEvent);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }

        registrationCounter.increment();
        log.info("User registered successfully userId={} role={}", savedUser.getId(), savedUser.getRole());

        return RegisterResponse.of(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name());
    }

    // ── Use Case: Login ──────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email={}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    loginFailureCounter.increment();
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (!user.isEnabled()) {
            loginFailureCounter.increment();
            throw new InvalidCredentialsException("Account is disabled");
        }

        if (user.isAccountLocked()) {
            loginFailureCounter.increment();
            throw new AccountLockedException(
                    "Account locked due to too many failed attempts. Try again after %s"
                    .formatted(user.getLockedUntil()));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepository.save(user);
            loginFailureCounter.increment();
            log.warn("Failed login attempt for email={} attempts={}", request.email(), user.getFailedLoginAttempts());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);

        String accessToken = tokenService.generateAccessToken(user);
        String rawRefreshToken = tokenService.generateRefreshToken();

        // Store only the hash of the refresh token
        RefreshToken refreshToken = RefreshToken.create(
                user.getId(),
                hashToken(rawRefreshToken),
                Instant.now().plusSeconds(604800) // 7 days
        );
        refreshTokenRepository.save(refreshToken);

        loginSuccessCounter.increment();
        log.info("Login successful userId={}", user.getId());

        return AuthResponse.of(
                accessToken,
                tokenService.accessTokenExpiresInSeconds(),
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    // ── Use Case: Refresh Token ──────────────────────────────

    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.refreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            // Token is expired or revoked — log potential token reuse attack
            if (refreshToken.isRevoked()) {
                log.warn("SECURITY: Revoked refresh token reused for userId={}", refreshToken.getUserId());
                // Revoke ALL tokens for this user — possible token theft
                refreshTokenRepository.revokeAllByUserId(refreshToken.getUserId());
            }
            throw new InvalidTokenException("Refresh token is expired or revoked");
        }

        // Rotate: revoke old, issue new
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User not found for refresh token"));

        String newAccessToken = tokenService.generateAccessToken(user);
        String newRawRefreshToken = tokenService.generateRefreshToken();

        RefreshToken newRefreshToken = RefreshToken.create(
                user.getId(),
                hashToken(newRawRefreshToken),
                Instant.now().plusSeconds(604800)
        );
        refreshTokenRepository.save(newRefreshToken);

        log.info("Token refreshed for userId={}", user.getId());

        return AuthResponse.of(
                newAccessToken,
                tokenService.accessTokenExpiresInSeconds(),
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    // ── Use Case: Logout ─────────────────────────────────────

    public void logout(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
            log.info("User logged out, token revoked for userId={}", token.getUserId());
        });
    }

    // ── Helpers ──────────────────────────────────────────────

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
