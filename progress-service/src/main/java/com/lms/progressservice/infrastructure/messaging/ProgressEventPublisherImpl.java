package com.lms.progressservice.infrastructure.messaging;

import com.lms.progressservice.application.port.CourseProgressEventPublisher;
import com.lms.progressservice.domain.model.CourseProgress;
import com.lms.progressservice.infrastructure.messaging.event.CourseCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressEventPublisherImpl implements CourseProgressEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishCourseCompleted(CourseProgress progress) {
        CourseCompletedEvent event = CourseCompletedEvent.of(
                progress.getStudentId(),
                progress.getCourseId(),
                progress.getCourseTitle(),
                progress.getTotalLessons());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PROGRESS_EXCHANGE,
                RabbitMQConfig.COURSE_COMPLETED_KEY,
                event);

        log.info("Published CourseCompletedEvent eventId={} studentId={} courseId={}",
                event.eventId(), progress.getStudentId(), progress.getCourseId());
    }
}
