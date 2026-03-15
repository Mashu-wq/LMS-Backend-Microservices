package com.lms.searchservice.application.dto;

import java.util.List;

/**
 * SearchResponse — paginated search result (RFC-compatible pagination metadata).
 */
public record SearchResponse(
        List<CourseSearchResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {}
