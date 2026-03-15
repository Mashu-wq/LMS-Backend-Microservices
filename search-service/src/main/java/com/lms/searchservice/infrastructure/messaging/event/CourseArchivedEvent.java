package com.lms.searchservice.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * CourseArchivedEvent — inbound event from course-service.
 *
 * <p>Routing key: {@code lms.course.archived}. Triggers removal from search index.
 */
public record CourseArchivedEvent(
        UUID eventId,
        String courseId,
        UUID instructorId,
        Instant occurredAt
) {}
