package com.lms.courseservice.infrastructure.messaging;

import com.lms.courseservice.application.port.CourseEventPublisher;
import com.lms.courseservice.domain.model.Course;
import com.lms.courseservice.domain.model.Enrollment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventPublisherImpl implements CourseEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishCoursePublished(Course course) {
        var event = new CoursePublishedEvent(
                UUID.randomUUID(),
                course.getCourseId(),
                course.getTitle(),
                course.getDescription(),
                course.getShortDescription(),
                course.getInstructorId(),
                course.getInstructorName(),
                course.getCategory(),
                course.getLanguage(),
                course.getLevel(),
                course.getPrice(),
                course.getThumbnailUrl(),
                course.getTags(),
                course.getAverageRating(),
                course.getRatingCount(),
                course.getTotalEnrollments(),
                course.getPublishedAt(),
                Instant.now()
        );
        publish(RabbitMQConfig.COURSE_EXCHANGE, RabbitMQConfig.COURSE_PUBLISHED_KEY, event);
        log.info("Published CoursePublishedEvent courseId={}", course.getCourseId());
    }

    @Override
    public void publishStudentEnrolled(Enrollment enrollment) {
        var event = new StudentEnrolledEvent(
                UUID.randomUUID(), enrollment.getEnrollmentId(),
                enrollment.getCourseId(), enrollment.getCourseTitle(),
                enrollment.getStudentId(), enrollment.getInstructorId(),
                Instant.now()
        );
        publish(RabbitMQConfig.COURSE_EXCHANGE, RabbitMQConfig.STUDENT_ENROLLED_KEY, event);
        log.info("Published StudentEnrolledEvent enrollmentId={}", enrollment.getEnrollmentId());
    }

    @Override
    public void publishCourseArchived(Course course) {
        var event = new CourseArchivedEvent(
                UUID.randomUUID(), course.getCourseId(), course.getInstructorId(), Instant.now()
        );
        publish(RabbitMQConfig.COURSE_EXCHANGE, RabbitMQConfig.COURSE_ARCHIVED_KEY, event);
    }

    private void publish(String exchange, String routingKey, Object event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
        } catch (Exception e) {
            log.error("Failed to publish event to exchange={} key={}: {}", exchange, routingKey, e.getMessage());
        }
    }

    // ── Event Records ────────────────────────────────────────

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
            java.math.BigDecimal price,
            String thumbnailUrl,
            java.util.List<String> tags,
            double averageRating,
            int ratingCount,
            int totalEnrollments,
            Instant publishedAt,
            Instant occurredAt) {}

    public record StudentEnrolledEvent(
            UUID eventId, String enrollmentId, String courseId, String courseTitle,
            UUID studentId, UUID instructorId, Instant occurredAt) {}

    public record CourseArchivedEvent(
            UUID eventId, String courseId, UUID instructorId, Instant occurredAt) {}
}
