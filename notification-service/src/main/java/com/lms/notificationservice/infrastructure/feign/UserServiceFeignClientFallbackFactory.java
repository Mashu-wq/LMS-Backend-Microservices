package com.lms.notificationservice.infrastructure.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class UserServiceFeignClientFallbackFactory
        implements FallbackFactory<UserServiceFeignClient> {

    @Override
    public UserServiceFeignClient create(Throwable cause) {
        return userId -> {
            log.warn("user-service unavailable for userId={}: {}", userId, cause.getMessage());
            // Return a minimal profile — the consumer will skip the email or log a warning
            return new UserServiceFeignClient.UserProfileDto(
                    userId, null, "Platform", "User");
        };
    }
}
