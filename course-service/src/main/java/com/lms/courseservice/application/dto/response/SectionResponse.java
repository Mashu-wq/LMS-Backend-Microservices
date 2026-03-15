package com.lms.courseservice.application.dto.response;

import com.lms.courseservice.domain.model.Section;

import java.util.List;

public record SectionResponse(
        String sectionId,
        String title,
        String description,
        int orderIndex,
        int lessonCount,
        int totalDurationMinutes,
        List<LessonResponse> lessons
) {
    public static SectionResponse from(Section section) {
        return new SectionResponse(
                section.getSectionId(),
                section.getTitle(),
                section.getDescription(),
                section.getOrderIndex(),
                section.getLessonCount(),
                section.totalDurationMinutes(),
                section.getLessons().stream().map(LessonResponse::from).toList()
        );
    }
}
