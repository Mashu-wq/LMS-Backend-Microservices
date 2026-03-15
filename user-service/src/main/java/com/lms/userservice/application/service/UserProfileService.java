package com.lms.userservice.application.service;

import com.lms.userservice.application.dto.request.UpdateProfileRequest;
import com.lms.userservice.application.dto.response.PageResponse;
import com.lms.userservice.application.dto.response.UserProfileResponse;
import com.lms.userservice.domain.exception.UserProfileAlreadyExistsException;
import com.lms.userservice.domain.exception.UserProfileNotFoundException;
import com.lms.userservice.domain.model.UserProfile;
import com.lms.userservice.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * User Profile Application Service — all use cases for user profile management.
 *
 * <p>Cache strategy:
 * - GET by userId: cached in Redis (key = userId). TTL set in config (10 min).
 * - UPDATE: evicts the cache entry for that userId so next read hits DB.
 * - List/page endpoints: NOT cached (too many combinations, short TTL not worth it).
 *
 * <p>The cache annotation approach works here because profile reads are frequent
 * (every course enrollment check, progress lookup) but writes are rare.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserProfileService {

    private static final String CACHE_NAME = "user-profiles";

    private final UserProfileRepository userProfileRepository;

    // ── Use Case: Create Profile (event-driven) ──────────────

    public UserProfileResponse createProfile(UUID userId, String email,
                                              String firstName, String lastName, String role) {
        log.info("Creating user profile for userId={} role={}", userId, role);

        if (userProfileRepository.existsByUserId(userId)) {
            // Idempotent — if profile already exists, return it
            // This handles message redelivery from RabbitMQ gracefully
            log.warn("Profile already exists for userId={} — skipping creation (idempotent)", userId);
            return userProfileRepository.findByUserId(userId)
                    .map(UserProfileResponse::from)
                    .orElseThrow(() -> new UserProfileNotFoundException(userId));
        }

        UserProfile profile = UserProfile.create(userId, email, firstName, lastName, role);
        UserProfile saved = userProfileRepository.save(profile);

        log.info("User profile created for userId={}", userId);
        return UserProfileResponse.from(saved);
    }

    // ── Use Case: Get Profile ────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#userId")
    public UserProfileResponse getProfile(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'email:' + #email")
    public UserProfileResponse getProfileByEmail(String email) {
        return userProfileRepository.findByEmail(email)
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new UserProfileNotFoundException(email));
    }

    // ── Use Case: Update Profile ─────────────────────────────

    @CacheEvict(value = CACHE_NAME, key = "#userId")
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        log.info("Updating profile for userId={}", userId);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

        profile.updateProfile(
                request.firstName() != null ? request.firstName() : profile.getFirstName(),
                request.lastName()  != null ? request.lastName()  : profile.getLastName(),
                request.bio(),
                request.avatarUrl()
        );

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Profile updated for userId={}", userId);
        return UserProfileResponse.from(saved);
    }

    // ── Use Case: Admin Operations ───────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listProfiles(Pageable pageable) {
        Page<UserProfile> page = userProfileRepository.findAll(pageable);
        return PageResponse.from(page, UserProfileResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listProfilesByRole(String role, Pageable pageable) {
        Page<UserProfile> page = userProfileRepository.findByRole(role, pageable);
        return PageResponse.from(page, UserProfileResponse::from);
    }

    @CacheEvict(value = CACHE_NAME, key = "#userId")
    public void suspendUser(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
        profile.suspend();
        userProfileRepository.save(profile);
        log.info("User suspended userId={}", userId);
    }

    @CacheEvict(value = CACHE_NAME, key = "#userId")
    public void reactivateUser(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
        profile.reactivate();
        userProfileRepository.save(profile);
        log.info("User reactivated userId={}", userId);
    }
}
