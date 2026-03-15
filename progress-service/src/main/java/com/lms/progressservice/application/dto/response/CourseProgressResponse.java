package com.lms.progressservice.application.dto.response;

import com.lms.progressservice.domain.model.CourseProgress;
import com.lms.progressservice.domain.model.ProgressStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CourseProgressResponse(
        UUID           progressId,
        UUID           studentId,
        String         courseId,
        String         courseTitle,
        int            totalLessons,
        int            completedLessons,
        int            completionPercentage,
        Set<String>    completedLessonIds,
        ProgressStatus status,
        Instant        enrolledAt,
        Instant        completedAt,
        Instant        lastActivityAt
) {
    public static CourseProgressResponse from(CourseProgress p) {
        return new CourseProgressResponse(
                p.getProgressId(),
                p.getStudentId(),
                p.getCourseId(),
                p.getCourseTitle(),
                p.getTotalLessons(),
                p.getCompletedLessonIds().size(),
                p.completionPercentage(),
                p.getCompletedLessonIds(),
                p.getStatus(),
                p.getEnrolledAt(),
                p.getCompletedAt(),
                p.getLastActivityAt()
        );
    }
}
