package com.lms.searchservice.infrastructure.messaging;

import com.lms.searchservice.application.service.SearchService;
import com.lms.searchservice.infrastructure.messaging.event.CourseArchivedEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CourseArchivedEventConsumer — removes a course from the search index when archived.
 *
 * <p>Consumes from {@code lms.search.course-archived}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseArchivedEventConsumer {

    private final SearchService searchService;

    @RabbitListener(queues = RabbitMQConfig.SEARCH_COURSE_ARCHIVED_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void handleCourseArchived(
            CourseArchivedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received CourseArchivedEvent eventId={} courseId={}",
                event.eventId(), event.courseId());
        try {
            searchService.removeFromIndex(event.courseId());
            channel.basicAck(deliveryTag, false);
            log.info("CourseArchivedEvent processed courseId={}", event.courseId());

        } catch (IllegalArgumentException | NullPointerException e) {
            log.error("Permanent error processing CourseArchivedEvent courseId={}: {}",
                    event.courseId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.warn("Transient error processing CourseArchivedEvent courseId={}, requeuing: {}",
                    event.courseId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
