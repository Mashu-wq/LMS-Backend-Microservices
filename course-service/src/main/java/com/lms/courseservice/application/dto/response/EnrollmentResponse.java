package com.lms.courseservice.application.dto.response;

import com.lms.courseservice.domain.model.Enrollment;
import com.lms.courseservice.domain.model.Enrollment.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        String enrollmentId,
        String courseId,
        String courseTitle,
        UUID studentId,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant completedAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getEnrollmentId(),
                enrollment.getCourseId(),
                enrollment.getCourseTitle(),
                enrollment.getStudentId(),
                enrollment.getStatus(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt()
        );
    }
}
