package com.lms.notificationservice.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Fetches course metadata from course-service to enrich payment email content.
 */
@FeignClient(
        name = "course-service",
        fallbackFactory = CourseServiceFeignClientFallbackFactory.class
)
public interface CourseServiceFeignClient {

    @GetMapping("/courses/v1/internal/{courseId}")
    CourseInfoDto getCourseInfo(@PathVariable String courseId);

    record CourseInfoDto(String courseId, String title) {}
}
