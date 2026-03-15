package com.lms.notificationservice.infrastructure.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CourseServiceFeignClientFallbackFactory
        implements FallbackFactory<CourseServiceFeignClient> {

    @Override
    public CourseServiceFeignClient create(Throwable cause) {
        return courseId -> {
            log.warn("course-service unavailable for courseId={}: {}", courseId, cause.getMessage());
            return new CourseServiceFeignClient.CourseInfoDto(courseId, "Your Course");
        };
    }
}
