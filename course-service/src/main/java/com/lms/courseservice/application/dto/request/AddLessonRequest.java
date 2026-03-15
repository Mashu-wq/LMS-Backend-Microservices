package com.lms.courseservice.application.dto.request;

import com.lms.courseservice.domain.model.LessonType;
import jakarta.validation.constraints.*;

public record AddLessonRequest(

        @NotBlank(message = "Lesson title is required")
        @Size(max = 200)
        String title,

        @Size(max = 2000)
        String description,

        String contentUrl,

        @NotNull(message = "Lesson type is required")
        LessonType lessonType,

        @Min(value = 0, message = "Duration must be 0 or greater")
        int durationMinutes
) {}
