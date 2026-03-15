package com.lms.authservice.infrastructure.scheduler;

import com.lms.authservice.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job to clean up expired refresh tokens.
 * Runs daily at 2am — keeps the refresh_tokens table from growing unbounded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * *")  // 2am every day
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting expired refresh token cleanup");
        try {
            refreshTokenRepository.deleteExpired();
            log.info("Expired refresh token cleanup completed");
        } catch (Exception e) {
            log.error("Failed to cleanup expired tokens: {}", e.getMessage());
        }
    }
}
