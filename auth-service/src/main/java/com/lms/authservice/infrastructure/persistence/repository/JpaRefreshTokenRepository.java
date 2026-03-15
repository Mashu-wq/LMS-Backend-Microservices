package com.lms.authservice.infrastructure.persistence.repository;

import com.lms.authservice.infrastructure.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revoked = true, t.revokedAt = :now WHERE t.userId = :userId AND t.revoked = false")
    void revokeAllByUserId(UUID userId, Instant now);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :now")
    void deleteAllExpiredBefore(Instant now);
}
