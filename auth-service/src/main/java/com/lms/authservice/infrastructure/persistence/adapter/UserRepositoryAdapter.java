package com.lms.authservice.infrastructure.persistence.adapter;

import com.lms.authservice.domain.model.User;
import com.lms.authservice.domain.repository.UserRepository;
import com.lms.authservice.infrastructure.persistence.entity.UserEntity;
import com.lms.authservice.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.lms.authservice.infrastructure.persistence.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter: implements the domain UserRepository port using JPA.
 * This is the only class that knows about both the domain and JPA.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    @Override
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}
