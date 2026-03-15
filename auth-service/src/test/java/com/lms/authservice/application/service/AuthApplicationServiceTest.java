package com.lms.authservice.application.service;

import com.lms.authservice.application.dto.request.LoginRequest;
import com.lms.authservice.application.dto.request.RegisterRequest;
import com.lms.authservice.application.dto.response.AuthResponse;
import com.lms.authservice.application.dto.response.RegisterResponse;
import com.lms.authservice.application.port.EventPublisher;
import com.lms.authservice.application.port.PasswordEncoder;
import com.lms.authservice.application.port.TokenService;
import com.lms.authservice.domain.model.Role;
import com.lms.authservice.domain.model.User;
import com.lms.authservice.domain.repository.RefreshTokenRepository;
import com.lms.authservice.domain.repository.UserRepository;
import com.lms.authservice.infrastructure.security.exception.InvalidCredentialsException;
import com.lms.authservice.infrastructure.security.exception.UserAlreadyExistsException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthApplicationService")
class AuthApplicationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenService tokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EventPublisher eventPublisher;

    private AuthApplicationService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthApplicationService(
                userRepository, refreshTokenRepository, tokenService,
                passwordEncoder, eventPublisher, new SimpleMeterRegistry()
        );
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should register a new user and publish event")
        void shouldRegisterSuccessfully() {
            // given
            RegisterRequest request = new RegisterRequest(
                    "John", "Doe", "john@example.com", "Password123!", Role.STUDENT);

            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Password123!")).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            RegisterResponse response = authService.register(request);

            // then
            assertThat(response.email()).isEqualTo("john@example.com");
            assertThat(response.role()).isEqualTo("STUDENT");
            verify(eventPublisher).publishUserRegistered(any());
        }

        @Test
        @DisplayName("should throw when email already exists")
        void shouldThrowWhenEmailTaken() {
            // given
            RegisterRequest request = new RegisterRequest(
                    "Jane", "Doe", "jane@example.com", "Password123!", Role.STUDENT);
            when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

            // when / then
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("jane@example.com");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should return auth tokens on valid credentials")
        void shouldLoginSuccessfully() {
            // given
            User user = User.create("john@example.com", "hashed", "John", "Doe", Role.STUDENT);
            LoginRequest request = new LoginRequest("john@example.com", "Password123!");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Password123!", "hashed")).thenReturn(true);
            when(userRepository.save(any())).thenReturn(user);
            when(tokenService.generateAccessToken(any())).thenReturn("access-token");
            when(tokenService.generateRefreshToken()).thenReturn("refresh-token");
            when(tokenService.accessTokenExpiresInSeconds()).thenReturn(900L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // when
            AuthResponse response = authService.login(request);

            // then
            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.email()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("should throw on invalid password")
        void shouldThrowOnWrongPassword() {
            // given
            User user = User.create("john@example.com", "hashed", "John", "Doe", Role.STUDENT);
            LoginRequest request = new LoginRequest("john@example.com", "WrongPassword!");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPassword!", "hashed")).thenReturn(false);
            when(userRepository.save(any())).thenReturn(user);

            // when / then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("should throw on non-existent email")
        void shouldThrowOnUnknownEmail() {
            // given
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> authService.login(new LoginRequest("x@x.com", "pass")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }
}
