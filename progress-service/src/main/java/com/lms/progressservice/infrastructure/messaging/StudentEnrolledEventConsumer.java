package com.lms.progressservice.infrastructure.messaging;

import com.lms.progressservice.application.service.ProgressService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumes StudentEnrolledEvent from course-service.
 * Initialises a CourseProgress record so the student can begin tracking lessons.
 *
 * <p>ACK strategy:
 * <ul>
 *   <li>SUCCESS / idempotent (already exists) → basicAck</li>
 *   <li>Transient error (DB unavailable) → basicNack requeue=true (retry)</li>
 *   <li>Permanent error (bad payload) → basicNack requeue=false (→ DLQ)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentEnrolledEventConsumer {

    private final ProgressService progressService;

    @RabbitListener(queues = RabbitMQConfig.PROGRESS_ENROLL_QUEUE)
    public void handleStudentEnrolled(StudentEnrolledEvent event,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.info("Received StudentEnrolledEvent enrollmentId={} studentId={} courseId={}",
                event.enrollmentId(), event.studentId(), event.courseId());
        try {
            progressService.initializeProgress(
                    event.studentId(), event.courseId(), event.courseTitle());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to initialise progress for enrollmentId={}: {}",
                    event.enrollmentId(), e.getMessage(), e);
            boolean requeue = isTransient(e);
            channel.basicNack(deliveryTag, false, requeue);
        }
    }

    private boolean isTransient(Exception e) {
        return e instanceof org.springframework.dao.TransientDataAccessException;
    }

    // ── Local copy of event contract (no shared library) ───────────────────

    public record StudentEnrolledEvent(
            UUID    eventId,
            String  enrollmentId,
            String  courseId,
            String  courseTitle,
            UUID    studentId,
            UUID    instructorId,
            Instant occurredAt
    ) {}
}
