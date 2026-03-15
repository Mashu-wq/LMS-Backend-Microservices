package com.lms.progressservice.infrastructure.feign;

import com.lms.progressservice.application.port.CourseServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapts the Feign client to the {@link CourseServiceClient} port.
 * Isolates the application layer from Feign/Spring Cloud internals.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseServiceClientAdapter implements CourseServiceClient {

    private final CourseServiceFeignClient feignClient;

    @Override
    public Optional<Integer> getTotalLessons(String courseId) {
        try {
            var details = feignClient.getCourseDetails(courseId);
            int count = details.totalLessons();
            return count > 0 ? Optional.of(count) : Optional.empty();
        } catch (Exception e) {
            log.warn("Could not fetch lesson count for courseId={}: {}", courseId, e.getMessage());
            return Optional.empty();
        }
    }
}
