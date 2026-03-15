package com.lms.userservice.infrastructure.feign;

import com.lms.userservice.application.dto.response.UserProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resilience4j fallback factory for user-service Feign client.
 *
 * <p>When user-service is unavailable or the circuit breaker is open,
 * callers receive a minimal fallback response rather than an exception.
 * This is appropriate for non-critical reads (e.g., displaying instructor name).
 *
 * <p>For critical flows (e.g., payment requires valid user), callers should
 * NOT use the fallback and should let the exception propagate.
 */
@Slf4j
@Component
public class UserServiceFeignClientFallbackFactory implements FallbackFactory<UserServiceFeignClient> {

    @Override
    public UserServiceFeignClient create(Throwable cause) {
        return new UserServiceFeignClient() {
            @Override
            public UserProfileResponse getUserProfile(UUID userId) {
                log.warn("user-service unavailable, returning fallback for userId={}: {}",
                        userId, cause.getMessage());
                // Return a minimal "unknown user" response — callers should handle this gracefully
                return new UserProfileResponse(
                        userId, "unknown@unknown.com",
                        "Unknown", "User", "Unknown User",
                        null, null, "STUDENT", "ACTIVE", null
                );
            }
        };
    }
}
