package com.lms.userservice.domain.repository;

import com.lms.userservice.domain.model.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    UserProfile save(UserProfile profile);

    Optional<UserProfile> findByUserId(UUID userId);

    Optional<UserProfile> findByEmail(String email);

    boolean existsByUserId(UUID userId);

    Page<UserProfile> findAll(Pageable pageable);

    Page<UserProfile> findByRole(String role, Pageable pageable);
}
