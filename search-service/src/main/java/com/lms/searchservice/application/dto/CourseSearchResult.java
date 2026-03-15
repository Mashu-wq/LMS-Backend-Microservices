package com.lms.searchservice.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * CourseSearchResult — a single course in search results (public API DTO).
 */
public record CourseSearchResult(
        String courseId,
        String title,
        String description,
        String shortDescription,
        String instructorName,
        String category,
        String level,
        BigDecimal price,
        double averageRating,
        int ratingCount,
        int totalEnrollments,
        List<String> tags,
        String language,
        String thumbnailUrl,
        Instant publishedAt
) {}
