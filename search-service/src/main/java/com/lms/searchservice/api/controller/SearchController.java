package com.lms.searchservice.api.controller;

import com.lms.searchservice.application.dto.SearchResponse;
import com.lms.searchservice.application.dto.SuggestionResponse;
import com.lms.searchservice.application.service.SearchService;
import com.lms.searchservice.domain.model.SearchCriteria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * SearchController — public REST API for course discovery.
 *
 * <p>Endpoints:
 * <pre>
 *   GET /search/v1                  — full-text search with filters and pagination
 *   GET /search/v1/suggestions      — autocomplete title suggestions
 * </pre>
 *
 * <p>All endpoints are publicly accessible (no authentication required).
 * The gateway may apply rate limiting.
 */
@RestController
@RequestMapping("/search/v1")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;

    /**
     * Full-text course search with optional filters.
     *
     * @param q        Free-text query (title, description, instructor, category, tags)
     * @param category Filter by category (exact match)
     * @param level    Filter by level: BEGINNER | INTERMEDIATE | ADVANCED | ALL_LEVELS
     * @param minPrice Minimum price (inclusive)
     * @param maxPrice Maximum price (inclusive)
     * @param minRating Minimum average rating (0.0–5.0)
     * @param language Filter by language (e.g. "English")
     * @param sortBy   RELEVANCE | NEWEST | PRICE_ASC | PRICE_DESC | RATING
     * @param page     Zero-based page index (default 0)
     * @param size     Page size 1–50 (default 20)
     */
    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) @Min(0) @Max(5) Double minRating,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "RELEVANCE") String sortBy,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {

        SearchCriteria criteria = SearchCriteria.of(
                q, category, level, minPrice, maxPrice,
                minRating, language, sortBy, page, size);

        return ResponseEntity.ok(searchService.search(criteria));
    }

    /**
     * Title autocomplete suggestions for search-as-you-type.
     *
     * @param q     Prefix to match against course titles (min 2 chars)
     * @param limit Max suggestions returned (1–10, default 10)
     */
    @GetMapping("/suggestions")
    public ResponseEntity<SuggestionResponse> suggestions(
            @RequestParam @NotBlank @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(10) int limit) {

        return ResponseEntity.ok(searchService.getSuggestions(q, limit));
    }
}
