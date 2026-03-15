package com.lms.notificationservice.infrastructure.messaging;

import com.lms.notificationservice.application.dto.EmailMessage;
import com.lms.notificationservice.application.service.EmailService;
import com.lms.notificationservice.infrastructure.feign.UserServiceFeignClient;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCompletedEventConsumer {

    private final EmailService           emailService;
    private final UserServiceFeignClient userClient;

    @RabbitListener(queues = RabbitMQConfig.COURSE_COMPLETED_QUEUE)
    public void handleCourseCompleted(CourseCompletedEvent event,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.info("Sending course completion certificate studentId={} courseId={}",
                event.studentId(), event.courseId());
        try {
            var user = userClient.getUserProfile(event.studentId());

            if (user.email() == null) {
                log.warn("No email for studentId={} — skipping completion certificate", event.studentId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            emailService.send(new EmailMessage(
                    user.email(),
                    "You completed: " + event.courseTitle() + " 🎓",
                    "course-completed",
                    Map.of(
                            "studentName",  user.fullName(),
                            "courseTitle",  event.courseTitle(),
                            "totalLessons", event.totalLessons(),
                            "completedAt",  event.occurredAt()
                    )
            ));
            channel.basicAck(deliveryTag, false);
        } catch (EmailService.EmailDeliveryException e) {
            log.error("SMTP failure for completion certificate studentId={}: {}",
                    event.studentId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        } catch (Exception e) {
            log.error("Permanent error processing CourseCompletedEvent studentId={}: {}",
                    event.studentId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    public record CourseCompletedEvent(
            UUID    eventId,
            UUID    studentId,
            String  courseId,
            String  courseTitle,
            int     totalLessons,
            Instant occurredAt
    ) {}
}
