package com.lms.courseservice.application.dto.response;

import com.lms.courseservice.domain.model.Course;
import com.lms.courseservice.domain.model.CourseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourseResponse(
        String courseId,
        String title,
        String description,
        String shortDescription,
        UUID instructorId,
        String instructorName,
        CourseStatus status,
        String category,
        String language,
        String level,
        BigDecimal price,
        String thumbnailUrl,
        List<String> tags,
        List<SectionResponse> sections,
        int totalEnrollments,
        double averageRating,
        int ratingCount,
        int totalLessons,
        int totalDurationMinutes,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getCourseId(),
                course.getTitle(),
                course.getDescription(),
                course.getShortDescription(),
                course.getInstructorId(),
                course.getInstructorName(),
                course.getStatus(),
                course.getCategory(),
                course.getLanguage(),
                course.getLevel(),
                course.getPrice(),
                course.getThumbnailUrl(),
                course.getTags(),
                course.getSections().stream().map(SectionResponse::from).toList(),
                course.getTotalEnrollments(),
                course.getAverageRating(),
                course.getRatingCount(),
                course.totalLessonCount(),
                course.totalDurationMinutes(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getPublishedAt()
        );
    }
}
