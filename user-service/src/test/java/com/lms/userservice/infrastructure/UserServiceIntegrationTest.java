package com.lms.userservice.infrastructure;

import com.lms.userservice.application.service.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test using Testcontainers — spins up real PostgreSQL.
 * This tests the full stack: service → repository adapter → Flyway migrations → PostgreSQL.
 *
 * <p>Note: Redis is mocked via a NoOp cache manager for integration tests.
 * A separate @SpringBootTest with Redis container would test cache behavior.
 */
@SpringBootTest(properties = {
        "spring.config.import=",           // Skip config server in tests
        "spring.cache.type=none",          // Disable Redis for integration test
        "spring.rabbitmq.host=localhost",  // Skip RabbitMQ (won't connect, listener won't start)
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="  // Skip JWT validation
})
@Testcontainers
@DisplayName("UserService Integration Test")
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lms_users_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private UserProfileService userProfileService;

    @Test
    @DisplayName("should create and retrieve profile end-to-end")
    void shouldCreateAndRetrieveProfile() {
        UUID userId = UUID.randomUUID();

        var created = userProfileService.createProfile(
                userId, "integration@test.com", "Integration", "Test", "STUDENT");

        assertThat(created.userId()).isEqualTo(userId);
        assertThat(created.email()).isEqualTo("integration@test.com");

        var retrieved = userProfileService.getProfile(userId);
        assertThat(retrieved.userId()).isEqualTo(userId);
        assertThat(retrieved.fullName()).isEqualTo("Integration Test");
    }

    @Test
    @DisplayName("createProfile should be idempotent on duplicate call")
    void shouldHandleDuplicateCreateIdempotently() {
        UUID userId = UUID.randomUUID();

        userProfileService.createProfile(userId, "dup@test.com", "Dup", "User", "STUDENT");
        // Second call with same userId — should not throw, returns existing
        var result = userProfileService.createProfile(userId, "dup@test.com", "Dup", "User", "STUDENT");

        assertThat(result.userId()).isEqualTo(userId);
    }
}
