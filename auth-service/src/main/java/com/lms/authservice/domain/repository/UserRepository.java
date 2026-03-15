package com.lms.authservice.domain.repository;

import com.lms.authservice.domain.model.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository interface (port).
 * The domain defines WHAT it needs; infrastructure provides HOW.
 * No JPA, no Spring Data — just pure domain contracts.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
