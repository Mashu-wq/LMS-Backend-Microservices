package com.lms.authservice.infrastructure.persistence.mapper;

import com.lms.authservice.domain.model.RefreshToken;
import com.lms.authservice.infrastructure.persistence.entity.RefreshTokenEntity;
import org.mapstruct.Mapper;

@Mapper
public interface RefreshTokenPersistenceMapper {

    RefreshTokenEntity toEntity(RefreshToken token);

    default RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.isRevoked(),
                entity.getRevokedAt()
        );
    }
}
