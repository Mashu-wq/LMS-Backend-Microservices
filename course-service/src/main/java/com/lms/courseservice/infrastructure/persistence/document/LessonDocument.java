package com.lms.courseservice.infrastructure.persistence.document;

import com.lms.courseservice.domain.model.LessonType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonDocument {
    private String lessonId;
    private String title;
    private String description;
    private String contentUrl;
    private LessonType lessonType;
    private int durationMinutes;
    private int orderIndex;
    private boolean previewEnabled;
}
