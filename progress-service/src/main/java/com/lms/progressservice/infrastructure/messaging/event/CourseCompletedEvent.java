package com.lms.progressservice.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by progress-service when a student completes all lessons in a course.
 *
 * <p>notification-service consumes this to send a course completion certificate email.
 */
public record CourseCompletedEvent(
        UUID    eventId,
        UUID    studentId,
        String  courseId,
        String  courseTitle,
        int     totalLessons,
        Instant occurredAt
) {
    public static CourseCompletedEvent of(UUID studentId, String courseId,
                                           String courseTitle, int totalLessons) {
        return new CourseCompletedEvent(
                UUID.randomUUID(), studentId, courseId, courseTitle, totalLessons, Instant.now());
    }
}
