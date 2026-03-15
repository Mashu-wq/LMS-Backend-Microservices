package com.lms.searchservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * CourseIndex — domain read-model representing a course in the search index.
 *
 * <p>This is a pure domain record with no framework dependencies.
 * It is populated from CoursePublishedEvent and stored in MongoDB for full-text search.
 */
public record CourseIndex(
        String courseId,
        String title,
        String description,
        String shortDescription,
        String instructorId,
        String instructorName,
        String category,
        CourseLevel level,
        BigDecimal price,
        double averageRating,
        int ratingCount,
        int totalEnrollments,
        List<String> tags,
        String language,
        String thumbnailUrl,
        Instant publishedAt,
        Instant indexedAt
) {

    public static CourseIndex create(
            String courseId,
            String title,
            String description,
            String shortDescription,
            String instructorId,
            String instructorName,
            String category,
            CourseLevel level,
            BigDecimal price,
            List<String> tags,
            String language,
            String thumbnailUrl,
            Instant publishedAt) {

        return new CourseIndex(
                courseId, title, description, shortDescription,
                instructorId, instructorName, category, level,
                price, 0.0, 0, 0, tags, language, thumbnailUrl,
                publishedAt, Instant.now()
        );
    }

    public static CourseIndex reconstitute(
            String courseId, String title, String description, String shortDescription,
            String instructorId, String instructorName, String category,
            CourseLevel level, BigDecimal price, double averageRating,
            int ratingCount, int totalEnrollments, List<String> tags,
            String language, String thumbnailUrl,
            Instant publishedAt, Instant indexedAt) {

        return new CourseIndex(
                courseId, title, description, shortDescription,
                instructorId, instructorName, category, level,
                price, averageRating, ratingCount, totalEnrollments,
                tags, language, thumbnailUrl, publishedAt, indexedAt
        );
    }
}
