package com.lms.userservice.infrastructure.persistence.mapper;

import com.lms.userservice.domain.model.UserProfile;
import com.lms.userservice.infrastructure.persistence.entity.UserProfileEntity;
import org.mapstruct.Mapper;

@Mapper
public interface UserProfilePersistenceMapper {

    UserProfileEntity toEntity(UserProfile profile);

    default UserProfile toDomain(UserProfileEntity entity) {
        return UserProfile.reconstitute(
                entity.getUserId(),
                entity.getEmail(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getBio(),
                entity.getAvatarUrl(),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
