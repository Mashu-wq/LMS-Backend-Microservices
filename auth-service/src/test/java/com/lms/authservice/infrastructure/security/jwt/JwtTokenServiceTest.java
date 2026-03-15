package com.lms.authservice.infrastructure.security.jwt;

import com.lms.authservice.domain.model.Role;
import com.lms.authservice.domain.model.User;
import com.lms.authservice.infrastructure.security.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenService")
class JwtTokenServiceTest {

    private JwtTokenService tokenService;
    private User testUser;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        tokenService = new JwtTokenService(keyPair, 900_000L, "lms-test");
        testUser = User.create("test@example.com", "hashed", "Test", "User", Role.STUDENT);
    }

    @Test
    @DisplayName("should generate and validate access token")
    void shouldGenerateAndValidateToken() {
        String token = tokenService.generateAccessToken(testUser);
        assertThat(token).isNotBlank();

        Map<String, Object> claims = tokenService.validateAccessToken(token);
        assertThat(claims.get("email")).isEqualTo("test@example.com");
        assertThat(claims.get("role")).isEqualTo("STUDENT");
        assertThat(claims.get("sub")).isEqualTo(testUser.getId().toString());
    }

    @Test
    @DisplayName("should throw on tampered token")
    void shouldThrowOnTamperedToken() {
        String token = tokenService.generateAccessToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> tokenService.validateAccessToken(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("should generate unique refresh tokens")
    void shouldGenerateUniqueRefreshTokens() {
        String token1 = tokenService.generateRefreshToken();
        String token2 = tokenService.generateRefreshToken();

        assertThat(token1).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1.length()).isGreaterThan(32);
    }
}
