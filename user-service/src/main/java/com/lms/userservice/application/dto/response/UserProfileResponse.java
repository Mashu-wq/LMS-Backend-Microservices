package com.lms.userservice.application.dto.response;

import com.lms.userservice.domain.model.UserProfile;

import java.time.Instant;
import java.util.UUID;

/**
 * Public-facing DTO for user profile data.
 * This is the contract other services rely on via Feign clients.
 * Field names are stable — changing them is a breaking API change.
 */
public record UserProfileResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String bio,
        String avatarUrl,
        String role,
        String status,
        Instant createdAt
) {
    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getUserId(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.fullName(),
                profile.getBio(),
                profile.getAvatarUrl(),
                profile.getRole(),
                profile.getStatus().name(),
                profile.getCreatedAt()
        );
    }
}
