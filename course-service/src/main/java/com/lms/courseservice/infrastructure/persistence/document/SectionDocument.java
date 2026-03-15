package com.lms.courseservice.infrastructure.persistence.document;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionDocument {
    private String sectionId;
    private String title;
    private String description;
    private int orderIndex;
    private List<LessonDocument> lessons;
}
