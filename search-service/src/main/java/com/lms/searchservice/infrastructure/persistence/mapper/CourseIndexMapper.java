package com.lms.searchservice.infrastructure.persistence.mapper;

import com.lms.searchservice.domain.model.CourseIndex;
import com.lms.searchservice.domain.model.CourseLevel;
import com.lms.searchservice.infrastructure.persistence.document.CourseIndexDocument;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manual mapper between {@link CourseIndex} (domain) and {@link CourseIndexDocument} (MongoDB).
 */
@Component
public class CourseIndexMapper {

    public CourseIndex toDomain(CourseIndexDocument doc) {
        CourseLevel level = null;
        if (doc.getLevel() != null && !doc.getLevel().isBlank()) {
            try {
                level = CourseLevel.valueOf(doc.getLevel());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return CourseIndex.reconstitute(
                doc.getCourseId(),
                doc.getTitle(),
                doc.getDescription(),
                doc.getShortDescription(),
                doc.getInstructorId(),
                doc.getInstructorName(),
                doc.getCategory(),
                level,
                doc.getPrice(),
                doc.getAverageRating(),
                doc.getRatingCount(),
                doc.getTotalEnrollments(),
                doc.getTags() != null ? doc.getTags() : List.of(),
                doc.getLanguage(),
                doc.getThumbnailUrl(),
                doc.getPublishedAt(),
                doc.getIndexedAt()
        );
    }

    public CourseIndexDocument toDocument(CourseIndex domain) {
        CourseIndexDocument doc = new CourseIndexDocument();
        doc.setCourseId(domain.courseId());
        doc.setTitle(domain.title());
        doc.setDescription(domain.description());
        doc.setShortDescription(domain.shortDescription());
        doc.setInstructorId(domain.instructorId());
        doc.setInstructorName(domain.instructorName());
        doc.setCategory(domain.category());
        doc.setLevel(domain.level() != null ? domain.level().name() : null);
        doc.setPrice(domain.price());
        doc.setAverageRating(domain.averageRating());
        doc.setRatingCount(domain.ratingCount());
        doc.setTotalEnrollments(domain.totalEnrollments());
        doc.setTags(domain.tags());
        doc.setLanguage(domain.language());
        doc.setThumbnailUrl(domain.thumbnailUrl());
        doc.setPublishedAt(domain.publishedAt());
        doc.setIndexedAt(domain.indexedAt());
        return doc;
    }
}
