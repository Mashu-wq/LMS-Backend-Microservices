package com.lms.authservice.infrastructure.persistence.adapter;

import com.lms.authservice.domain.model.RefreshToken;
import com.lms.authservice.domain.repository.RefreshTokenRepository;
import com.lms.authservice.infrastructure.persistence.entity.RefreshTokenEntity;
import com.lms.authservice.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
import com.lms.authservice.infrastructure.persistence.repository.JpaRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final JpaRefreshTokenRepository jpaRepository;
    private final RefreshTokenPersistenceMapper mapper;

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenEntity entity = mapper.toEntity(token);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public void revokeAllByUserId(UUID userId) {
        jpaRepository.revokeAllByUserId(userId, Instant.now());
    }

    @Override
    public void deleteExpired() {
        jpaRepository.deleteAllExpiredBefore(Instant.now());
    }
}
