package com.lms.searchservice.infrastructure.messaging;

import com.lms.searchservice.application.service.SearchService;
import com.lms.searchservice.domain.model.CourseIndex;
import com.lms.searchservice.domain.model.CourseLevel;
import com.lms.searchservice.infrastructure.messaging.event.CoursePublishedEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * CoursePublishedEventConsumer — indexes a course when it is published.
 *
 * <p>Consumes from {@code lms.search.course-published} (bound to lms.course.events exchange).
 * Uses manual acknowledgment: permanent errors (bad data) go to DLQ, transient errors are requeued.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoursePublishedEventConsumer {

    private final SearchService searchService;

    @RabbitListener(queues = RabbitMQConfig.SEARCH_COURSE_PUBLISHED_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void handleCoursePublished(
            CoursePublishedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received CoursePublishedEvent eventId={} courseId={}",
                event.eventId(), event.courseId());
        try {
            CourseLevel level = parseLevelSafely(event.level());

            CourseIndex courseIndex = CourseIndex.create(
                    event.courseId(),
                    event.title(),
                    event.description(),
                    event.shortDescription(),
                    event.instructorId() != null ? event.instructorId().toString() : null,
                    event.instructorName(),
                    event.category(),
                    level,
                    event.price(),
                    event.tags() != null ? event.tags() : List.of(),
                    event.language(),
                    event.thumbnailUrl(),
                    event.publishedAt()
            );

            searchService.indexCourse(courseIndex);
            channel.basicAck(deliveryTag, false);
            log.info("CoursePublishedEvent processed courseId={}", event.courseId());

        } catch (IllegalArgumentException | NullPointerException e) {
            // Permanent error: bad/missing data → send to DLQ
            log.error("Permanent error processing CoursePublishedEvent courseId={}: {}",
                    event.courseId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            // Transient error (DB unavailable, etc.) → requeue
            log.warn("Transient error processing CoursePublishedEvent courseId={}, requeuing: {}",
                    event.courseId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private CourseLevel parseLevelSafely(String level) {
        if (level == null || level.isBlank()) return CourseLevel.ALL_LEVELS;
        try {
            return CourseLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CourseLevel.ALL_LEVELS;
        }
    }
}
