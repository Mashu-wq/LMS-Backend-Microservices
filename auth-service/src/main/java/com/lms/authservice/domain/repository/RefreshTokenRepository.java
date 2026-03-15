package com.lms.authservice.domain.repository;

import com.lms.authservice.domain.model.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke all active refresh tokens for a user (logout all devices). */
    void revokeAllByUserId(UUID userId);

    /** Cleanup expired tokens — called by a scheduled job. */
    void deleteExpired();
}
