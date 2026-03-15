package com.lms.userservice.application.service;

import com.lms.userservice.application.dto.request.UpdateProfileRequest;
import com.lms.userservice.application.dto.response.UserProfileResponse;
import com.lms.userservice.domain.exception.UserProfileNotFoundException;
import com.lms.userservice.domain.model.UserProfile;
import com.lms.userservice.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService")
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        // We can't use @InjectMocks because @Cacheable needs a Spring context
        // For unit tests, we test without cache — integration tests cover cache behavior
        userProfileService = new UserProfileService(userProfileRepository);
    }

    @Nested
    @DisplayName("createProfile()")
    class CreateProfile {

        @Test
        @DisplayName("should create a new profile")
        void shouldCreateProfile() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "test@test.com", "Test", "User", "STUDENT");

            when(userProfileRepository.existsByUserId(userId)).thenReturn(false);
            when(userProfileRepository.save(any())).thenReturn(profile);

            UserProfileResponse result = userProfileService.createProfile(
                    userId, "test@test.com", "Test", "User", "STUDENT");

            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.email()).isEqualTo("test@test.com");
            verify(userProfileRepository).save(any());
        }

        @Test
        @DisplayName("should be idempotent when profile already exists")
        void shouldBeIdempotentIfExists() {
            UUID userId = UUID.randomUUID();
            UserProfile existing = UserProfile.create(userId, "test@test.com", "Test", "User", "STUDENT");

            when(userProfileRepository.existsByUserId(userId)).thenReturn(true);
            when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

            UserProfileResponse result = userProfileService.createProfile(
                    userId, "test@test.com", "Test", "User", "STUDENT");

            assertThat(result.userId()).isEqualTo(userId);
            verify(userProfileRepository, never()).save(any()); // no second save
        }
    }

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfile {

        @Test
        @DisplayName("should update profile fields")
        void shouldUpdateProfile() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "test@test.com", "Old", "Name", "STUDENT");
            UpdateProfileRequest request = new UpdateProfileRequest("New", "Name", "My bio", null);

            when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserProfileResponse result = userProfileService.updateProfile(userId, request);

            assertThat(result.firstName()).isEqualTo("New");
            assertThat(result.bio()).isEqualTo("My bio");
        }

        @Test
        @DisplayName("should throw when profile not found")
        void shouldThrowWhenNotFound() {
            UUID userId = UUID.randomUUID();
            when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                userProfileService.updateProfile(userId, new UpdateProfileRequest("A", "B", null, null))
            ).isInstanceOf(UserProfileNotFoundException.class);
        }
    }
}
