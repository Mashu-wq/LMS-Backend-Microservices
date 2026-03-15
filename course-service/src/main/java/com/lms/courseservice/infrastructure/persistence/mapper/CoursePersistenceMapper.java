package com.lms.courseservice.infrastructure.persistence.mapper;

import com.lms.courseservice.domain.model.*;
import com.lms.courseservice.infrastructure.persistence.document.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written mapper — MapStruct struggles with deeply nested domain objects
 * that use private constructors and reconstitute() factories.
 * Manual mapping here is clearer and less error-prone.
 */
@Component
public class CoursePersistenceMapper {

    public CourseDocument toDocument(Course course) {
        return CourseDocument.builder()
                .courseId(course.getCourseId())
                .title(course.getTitle())
                .description(course.getDescription())
                .shortDescription(course.getShortDescription())
                .instructorId(course.getInstructorId())
                .instructorName(course.getInstructorName())
                .status(course.getStatus())
                .category(course.getCategory())
                .language(course.getLanguage())
                .level(course.getLevel())
                .price(course.getPrice())
                .thumbnailUrl(course.getThumbnailUrl())
                .tags(new ArrayList<>(course.getTags()))
                .sections(course.getSections().stream().map(this::toDocument).toList())
                .totalEnrollments(course.getTotalEnrollments())
                .averageRating(course.getAverageRating())
                .ratingCount(course.getRatingCount())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .publishedAt(course.getPublishedAt())
                .build();
    }

    public Course toDomain(CourseDocument doc) {
        List<Section> sections = doc.getSections() != null
                ? doc.getSections().stream().map(this::toDomain).toList()
                : List.of();

        return Course.reconstitute(
                doc.getCourseId(), doc.getTitle(), doc.getDescription(),
                doc.getShortDescription(), doc.getInstructorId(), doc.getInstructorName(),
                doc.getStatus(), doc.getCategory(), doc.getLanguage(), doc.getLevel(),
                doc.getPrice(), doc.getThumbnailUrl(),
                doc.getTags() != null ? doc.getTags() : List.of(),
                sections, doc.getTotalEnrollments(), doc.getAverageRating(),
                doc.getRatingCount(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getPublishedAt()
        );
    }

    private SectionDocument toDocument(Section section) {
        return SectionDocument.builder()
                .sectionId(section.getSectionId())
                .title(section.getTitle())
                .description(section.getDescription())
                .orderIndex(section.getOrderIndex())
                .lessons(section.getLessons().stream().map(this::toDocument).toList())
                .build();
    }

    private Section toDomain(SectionDocument doc) {
        List<Lesson> lessons = doc.getLessons() != null
                ? doc.getLessons().stream().map(this::toDomain).toList()
                : List.of();
        return Section.reconstitute(doc.getSectionId(), doc.getTitle(),
                doc.getDescription(), doc.getOrderIndex(), lessons);
    }

    private LessonDocument toDocument(Lesson lesson) {
        return LessonDocument.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .description(lesson.getDescription())
                .contentUrl(lesson.getContentUrl())
                .lessonType(lesson.getLessonType())
                .durationMinutes(lesson.getDurationMinutes())
                .orderIndex(lesson.getOrderIndex())
                .previewEnabled(lesson.isPreviewEnabled())
                .build();
    }

    private Lesson toDomain(LessonDocument doc) {
        return Lesson.reconstitute(doc.getLessonId(), doc.getTitle(), doc.getDescription(),
                doc.getContentUrl(), doc.getLessonType(), doc.getDurationMinutes(),
                doc.getOrderIndex(), doc.isPreviewEnabled());
    }
}
