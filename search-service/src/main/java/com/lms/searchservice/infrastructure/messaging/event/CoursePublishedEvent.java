package com.lms.searchservice.infrastructure.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CoursePublishedEvent — inbound event from course-service.
 *
 * <p>Published to exchange {@code lms.course.events} with routing key {@code lms.course.published}.
 * All fields required to build a complete search index entry without additional Feign calls.
 */
public record CoursePublishedEvent(
        UUID eventId,
        String courseId,
        String title,
        String description,
        String shortDescription,
        UUID instructorId,
        String instructorName,
        String category,
        String language,
        String level,
        BigDecimal price,
        String thumbnailUrl,
        List<String> tags,
        double averageRating,
        int ratingCount,
        int totalEnrollments,
        Instant publishedAt,
        Instant occurredAt
) {}
