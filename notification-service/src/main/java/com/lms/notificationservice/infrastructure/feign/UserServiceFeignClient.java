package com.lms.notificationservice.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Fetches user profile data from user-service to enrich email content.
 * Called only for events that don't carry the recipient email directly.
 */
@FeignClient(
        name = "user-service",
        fallbackFactory = UserServiceFeignClientFallbackFactory.class
)
public interface UserServiceFeignClient {

    @GetMapping("/users/v1/internal/{userId}")
    UserProfileDto getUserProfile(@PathVariable UUID userId);

    record UserProfileDto(
            UUID   userId,
            String email,
            String firstName,
            String lastName
    ) {
        public String fullName() {
            return firstName + " " + lastName;
        }
    }
}
