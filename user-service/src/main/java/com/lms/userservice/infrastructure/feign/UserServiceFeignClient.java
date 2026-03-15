package com.lms.userservice.infrastructure.feign;

import com.lms.userservice.application.dto.response.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client declaration for user-service.
 *
 * <p>This interface is declared in user-service but USED by other services
 * (course-service, progress-service, etc.).
 *
 * <p>In a real multi-repo project, this would live in a shared client library.
 * For this mono-repo setup, other services will copy this interface locally
 * to avoid cross-module compile-time coupling.
 *
 * <p>The /internal/** path bypasses gateway authentication — it's only
 * accessible within the Docker/k8s network via service name.
 */
@FeignClient(
    name = "user-service",
    path = "/users/v1",
    fallbackFactory = UserServiceFeignClientFallbackFactory.class
)
public interface UserServiceFeignClient {

    @GetMapping("/internal/{userId}")
    UserProfileResponse getUserProfile(@PathVariable UUID userId);
}
