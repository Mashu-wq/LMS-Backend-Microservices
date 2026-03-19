package com.lms.authservice.infrastructure.persistence.mapper;

import com.lms.authservice.domain.model.RefreshToken;
import com.lms.authservice.infrastructure.persistence.entity.RefreshTokenEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface RefreshTokenPersistenceMapper {

    @Mapping(target = "deviceName", ignore = true)
    @Mapping(target = "ipAddress", ignore = true)
    @Mapping(target = "userAgent", ignore = true)
    @Mapping(target = "lastUsedAt", ignore = true)
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