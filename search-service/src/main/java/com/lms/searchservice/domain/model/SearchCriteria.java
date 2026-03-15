package com.lms.searchservice.domain.model;

import java.math.BigDecimal;

/**
 * SearchCriteria — value object encapsulating all search filter and sort parameters.
 */
public record SearchCriteria(
        String query,
        String category,
        CourseLevel level,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minRating,
        String language,
        SortOption sortBy,
        int page,
        int size
) {

    private static final int MAX_PAGE_SIZE = 50;

    public static SearchCriteria of(
            String query, String category, String level,
            BigDecimal minPrice, BigDecimal maxPrice, Double minRating,
            String language, String sortBy, int page, int size) {

        CourseLevel levelEnum = null;
        if (level != null && !level.isBlank()) {
            try {
                levelEnum = CourseLevel.valueOf(level.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // invalid level value → no filter
            }
        }

        SortOption sortEnum = SortOption.RELEVANCE;
        if (sortBy != null && !sortBy.isBlank()) {
            try {
                sortEnum = SortOption.valueOf(sortBy.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // invalid sort value → default RELEVANCE
            }
        }

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        return new SearchCriteria(
                query != null && !query.isBlank() ? query.trim() : null,
                category != null && !category.isBlank() ? category.trim() : null,
                levelEnum,
                minPrice,
                maxPrice,
                minRating,
                language != null && !language.isBlank() ? language.trim() : null,
                sortEnum,
                safePage,
                safeSize
        );
    }

    public boolean hasTextQuery() {
        return query != null && !query.isBlank();
    }
}
