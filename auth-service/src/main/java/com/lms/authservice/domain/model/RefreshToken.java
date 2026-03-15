package com.lms.authservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * RefreshToken — domain model for long-lived refresh tokens.
 *
 * <p>Refresh tokens are stored in the database (unlike access tokens which are
 * stateless JWTs). This enables:
 * - Token revocation (logout invalidates the stored token)
 * - Rotation (each refresh issues a new token and invalidates the old one)
 * - Detection of token reuse attacks (if a rotated token is used again, revoke all)
 */
public class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;  // SHA-256 hash — never store raw tokens
    private final Instant expiresAt;
    private final Instant createdAt;
    private boolean revoked;
    private Instant revokedAt;

    private RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.revoked = false;
    }

    public static RefreshToken create(UUID userId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(UUID.randomUUID(), userId, tokenHash, expiresAt);
    }

    public static RefreshToken reconstitute(UUID id, UUID userId, String tokenHash,
                                            Instant expiresAt, Instant createdAt,
                                            boolean revoked, Instant revokedAt) {
        RefreshToken token = new RefreshToken(id, userId, tokenHash, expiresAt);
        token.revoked = revoked;
        token.revokedAt = revokedAt;
        return token;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }

    public void revoke() {
        this.revoked = true;
        this.revokedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRevoked() { return revoked; }
    public Instant getRevokedAt() { return revokedAt; }
}
