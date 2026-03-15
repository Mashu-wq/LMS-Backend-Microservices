package com.lms.progressservice.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Feign client for course-service internal endpoint.
 * Used to fetch the total lesson count when initialising a student's progress record.
 */
@FeignClient(
        name = "course-service",
        fallbackFactory = CourseServiceFeignClientFallbackFactory.class
)
public interface CourseServiceFeignClient {

    @GetMapping("/courses/v1/internal/{courseId}")
    CourseDetailsDto getCourseDetails(@PathVariable String courseId);

    // ── Minimal DTO — only fields we need, Jackson ignores the rest ─────────

    record CourseDetailsDto(
            String          courseId,
            String          title,
            List<SectionDto> sections
    ) {
        /** Counts every lesson across all sections. */
        public int totalLessons() {
            if (sections == null) return 0;
            return sections.stream()
                    .mapToInt(s -> s.lessons() == null ? 0 : s.lessons().size())
                    .sum();
        }

        record SectionDto(String sectionId, List<LessonDto> lessons) {}
        record LessonDto(String lessonId, String title, BigDecimal durationMinutes) {}
    }
}
