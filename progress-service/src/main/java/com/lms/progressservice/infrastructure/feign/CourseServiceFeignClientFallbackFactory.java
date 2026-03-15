package com.lms.progressservice.infrastructure.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for course-service Feign calls.
 *
 * <p>If course-service is unavailable, progress initialisation continues with
 * {@code totalLessons = 0}. The lesson count will be updated when the course
 * is fetched later, or progress percentage will simply show 0 until it's available.
 */
@Slf4j
@Component
public class CourseServiceFeignClientFallbackFactory
        implements FallbackFactory<CourseServiceFeignClient> {

    @Override
    public CourseServiceFeignClient create(Throwable cause) {
        return courseId -> {
            log.warn("course-service unavailable when fetching details for courseId={}: {}",
                    courseId, cause.getMessage());
            // Return an empty response — totalLessons() will return 0
            return new CourseServiceFeignClient.CourseDetailsDto(courseId, "Unknown", null);
        };
    }
}
