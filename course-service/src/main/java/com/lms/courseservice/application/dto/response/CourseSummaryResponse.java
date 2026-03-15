package com.lms.courseservice.application.dto.response;

import com.lms.courseservice.domain.model.Course;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight course summary — used in paginated catalog listings.
 * Does NOT include sections/lessons (avoids large payloads in list views).
 */
public record CourseSummaryResponse(
        String courseId,
        String title,
        String shortDescription,
        UUID instructorId,
        String instructorName,
        String category,
        String language,
        String level,
        BigDecimal price,
        String thumbnailUrl,
        List<String> tags,
        int totalEnrollments,
        double averageRating,
        int totalLessons,
        int totalDurationMinutes,
        Instant publishedAt
) {
    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(
                course.getCourseId(),
                course.getTitle(),
                course.getShortDescription(),
                course.getInstructorId(),
                course.getInstructorName(),
                course.getCategory(),
                course.getLanguage(),
                course.getLevel(),
                course.getPrice(),
                course.getThumbnailUrl(),
                course.getTags(),
                course.getTotalEnrollments(),
                course.getAverageRating(),
                course.totalLessonCount(),
                course.totalDurationMinutes(),
                course.getPublishedAt()
        );
    }
}
