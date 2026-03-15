package com.lms.authservice.infrastructure.security.jwt;

import com.lms.authservice.application.port.TokenService;
import com.lms.authservice.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT Token Service — RSA-256 asymmetric signing.
 *
 * <p>Why RSA over HMAC?
 * - The gateway and other services can validate tokens using ONLY the public key.
 * - The private key never leaves auth-service, eliminating a class of secret-sharing risk.
 * - The JWKS endpoint exposes the public key, so gateway auto-fetches it.
 *
 * <p>Token structure (claims):
 *   sub  — user UUID
 *   email — user email
 *   role  — single role (ADMIN | INSTRUCTOR | STUDENT)
 *   iss   — "lms-platform"
 *   iat   — issued at
 *   exp   — expiry
 *   jti   — unique token ID (for blacklisting if needed)
 */
@Slf4j
@Component
public class JwtTokenService implements TokenService {

    private final KeyPair keyPair;
    private final long accessTokenExpirationMs;
    private final String issuer;

    public JwtTokenService(KeyPair rsaKeyPair,
                           @Value("${jwt.access-token-expiration:900000}") long accessTokenExpirationMs,
                           @Value("${jwt.issuer:lms-platform}") String issuer) {
        this.keyPair = rsaKeyPair;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.issuer = issuer;
    }

    @Override
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .signWith(keyPair.getPrivate())
                .compact();
    }

    @Override
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Override
    public Map<String, Object> validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Map.copyOf(claims);
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed");
            throw new com.lms.authservice.infrastructure.security.exception.InvalidTokenException("Invalid token signature");
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.debug("JWT token expired");
            throw new com.lms.authservice.infrastructure.security.exception.InvalidTokenException("Token has expired");
        } catch (Exception e) {
            log.warn("JWT validation error: {}", e.getMessage());
            throw new com.lms.authservice.infrastructure.security.exception.InvalidTokenException("Invalid token");
        }
    }

    @Override
    public long accessTokenExpiresInSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    /** Exposes the public key for the JWKS endpoint. */
    public java.security.interfaces.RSAPublicKey getPublicKey() {
        return (java.security.interfaces.RSAPublicKey) keyPair.getPublic();
    }
}
