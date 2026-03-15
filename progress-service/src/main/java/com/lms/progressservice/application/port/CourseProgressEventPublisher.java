package com.lms.progressservice.application.port;

import com.lms.progressservice.domain.model.CourseProgress;

public interface CourseProgressEventPublisher {

    void publishCourseCompleted(CourseProgress progress);
}
