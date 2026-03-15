package com.lms.authservice.infrastructure.persistence.mapper;

import com.lms.authservice.domain.model.User;
import com.lms.authservice.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between domain User and JPA UserEntity.
 * componentModel=spring is set globally via compiler arg in root POM.
 */
@Mapper
public interface UserPersistenceMapper {

    @Mapping(target = "id", source = "id")
    UserEntity toEntity(User user);

    default User toDomain(UserEntity entity) {
        return User.reconstitute(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getRole(),
                entity.isEnabled(),
                entity.isEmailVerified(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getFailedLoginAttempts(),
                entity.getLockedUntil()
        );
    }
}
