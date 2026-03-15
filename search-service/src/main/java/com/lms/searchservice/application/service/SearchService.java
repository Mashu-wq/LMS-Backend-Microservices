package com.lms.searchservice.application.service;

import com.lms.searchservice.application.dto.CourseSearchResult;
import com.lms.searchservice.application.dto.SearchResponse;
import com.lms.searchservice.application.dto.SuggestionResponse;
import com.lms.searchservice.domain.model.CourseIndex;
import com.lms.searchservice.domain.model.SearchCriteria;
import com.lms.searchservice.domain.repository.CourseIndexRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SearchService — application use-case orchestrator.
 *
 * <p>Responsibilities:
 * - Index courses when they are published (from RabbitMQ events)
 * - Remove courses from index when archived
 * - Serve full-text search queries with filters, pagination, and sorting
 * - Provide autocomplete suggestions
 *
 * <p>Results are cached in Redis (2-min TTL) and evicted when the index changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final CourseIndexRepository courseIndexRepository;
    private final MeterRegistry meterRegistry;

    // ── Indexing ──────────────────────────────────────────────

    @CacheEvict(cacheNames = {"searchResults", "suggestions"}, allEntries = true)
    public void indexCourse(CourseIndex courseIndex) {
        log.info("Indexing course courseId={} title='{}'", courseIndex.courseId(), courseIndex.title());
        courseIndexRepository.save(courseIndex);
        Counter.builder("search.courses.indexed")
                .tag("category", courseIndex.category() != null ? courseIndex.category() : "unknown")
                .register(meterRegistry)
                .increment();
        log.info("Course indexed successfully courseId={}", courseIndex.courseId());
    }

    @CacheEvict(cacheNames = {"searchResults", "suggestions"}, allEntries = true)
    public void removeFromIndex(String courseId) {
        log.info("Removing course from index courseId={}", courseId);
        courseIndexRepository.deleteById(courseId);
        meterRegistry.counter("search.courses.removed").increment();
        log.info("Course removed from index courseId={}", courseId);
    }

    // ── Search ────────────────────────────────────────────────

    @Cacheable(cacheNames = "searchResults")
    public SearchResponse search(SearchCriteria criteria) {
        log.debug("Executing search query='{}' category='{}' level='{}' page={} size={}",
                criteria.query(), criteria.category(), criteria.level(),
                criteria.page(), criteria.size());

        Page<CourseIndex> results = courseIndexRepository.search(criteria);

        Counter.builder("search.queries.total")
                .tag("has_query", String.valueOf(criteria.hasTextQuery()))
                .register(meterRegistry)
                .increment();

        List<CourseSearchResult> dtos = results.getContent()
                .stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

        return new SearchResponse(
                dtos,
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.isLast()
        );
    }

    @Cacheable(cacheNames = "suggestions", key = "#prefix")
    public SuggestionResponse getSuggestions(String prefix, int limit) {
        int safeLimit = Math.min(limit, 10);
        List<String> suggestions = courseIndexRepository.findTitleSuggestions(prefix.trim(), safeLimit);
        return new SuggestionResponse(suggestions);
    }

    // ── Mapping ───────────────────────────────────────────────

    private CourseSearchResult toSearchResult(CourseIndex c) {
        return new CourseSearchResult(
                c.courseId(),
                c.title(),
                c.description(),
                c.shortDescription(),
                c.instructorName(),
                c.category(),
                c.level() != null ? c.level().name() : null,
                c.price(),
                c.averageRating(),
                c.ratingCount(),
                c.totalEnrollments(),
                c.tags(),
                c.language(),
                c.thumbnailUrl(),
                c.publishedAt()
        );
    }
}
