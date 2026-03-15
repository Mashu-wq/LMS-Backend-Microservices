package com.lms.courseservice.application.dto.response;

import com.lms.courseservice.domain.model.Lesson;
import com.lms.courseservice.domain.model.LessonType;

public record LessonResponse(
        String lessonId,
        String title,
        String description,
        String contentUrl,
        LessonType lessonType,
        int durationMinutes,
        int orderIndex,
        boolean previewEnabled
) {
    public static LessonResponse from(Lesson lesson) {
        return new LessonResponse(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getContentUrl(),
                lesson.getLessonType(),
                lesson.getDurationMinutes(),
                lesson.getOrderIndex(),
                lesson.isPreviewEnabled()
        );
    }
}
