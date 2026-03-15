package com.lms.userservice.infrastructure.persistence.repository;

import com.lms.userservice.infrastructure.persistence.entity.UserProfileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaUserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    Optional<UserProfileEntity> findByUserId(UUID userId);

    Optional<UserProfileEntity> findByEmail(String email);

    boolean existsByUserId(UUID userId);

    Page<UserProfileEntity> findByRole(String role, Pageable pageable);
}
