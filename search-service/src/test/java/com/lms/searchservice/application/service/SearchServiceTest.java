package com.lms.searchservice.application.service;

import com.lms.searchservice.application.dto.SearchResponse;
import com.lms.searchservice.application.dto.SuggestionResponse;
import com.lms.searchservice.domain.model.CourseIndex;
import com.lms.searchservice.domain.model.CourseLevel;
import com.lms.searchservice.domain.model.SearchCriteria;
import com.lms.searchservice.domain.repository.CourseIndexRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SearchService — mocks the repository and verifies orchestration logic.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private CourseIndexRepository courseIndexRepository;

    private MeterRegistry meterRegistry;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        searchService = new SearchService(courseIndexRepository, meterRegistry);
    }

    // ── indexCourse ───────────────────────────────────────────

    @Test
    void indexCourse_delegatesToRepository() {
        CourseIndex course = buildCourse("c1", "Java Basics");

        searchService.indexCourse(course);

        verify(courseIndexRepository).save(course);
    }

    @Test
    void indexCourse_incrementsMetric() {
        CourseIndex course = buildCourse("c1", "Java Basics");

        searchService.indexCourse(course);

        assertThat(meterRegistry.counter("search.courses.indexed",
                "category", "Programming").count()).isEqualTo(1.0);
    }

    // ── removeFromIndex ───────────────────────────────────────

    @Test
    void removeFromIndex_delegatesToRepository() {
        searchService.removeFromIndex("c1");

        verify(courseIndexRepository).deleteById("c1");
    }

    @Test
    void removeFromIndex_incrementsMetric() {
        searchService.removeFromIndex("c1");

        assertThat(meterRegistry.counter("search.courses.removed").count()).isEqualTo(1.0);
    }

    // ── search ────────────────────────────────────────────────

    @Test
    void search_returnsPagedResults() {
        CourseIndex course = buildCourse("c1", "Java Spring Boot");
        when(courseIndexRepository.search(any(SearchCriteria.class)))
                .thenReturn(new PageImpl<>(List.of(course), PageRequest.of(0, 20), 1));

        SearchCriteria criteria = SearchCriteria.of(
                "java", null, null, null, null, null, null, "RELEVANCE", 0, 20);
        SearchResponse response = searchService.search(criteria);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.last()).isTrue();
    }

    @Test
    void search_mapsFieldsToDto() {
        CourseIndex course = buildCourse("c1", "Spring Boot Course");
        when(courseIndexRepository.search(any()))
                .thenReturn(new PageImpl<>(List.of(course), PageRequest.of(0, 20), 1));

        SearchCriteria criteria = SearchCriteria.of(null, null, null, null, null, null, null, "NEWEST", 0, 20);
        SearchResponse response = searchService.search(criteria);

        var result = response.content().get(0);
        assertThat(result.courseId()).isEqualTo("c1");
        assertThat(result.title()).isEqualTo("Spring Boot Course");
        assertThat(result.instructorName()).isEqualTo("Jane Smith");
        assertThat(result.level()).isEqualTo("INTERMEDIATE");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("39.99"));
    }

    @Test
    void search_emptyResults_returnsEmptyPage() {
        when(courseIndexRepository.search(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        SearchCriteria criteria = SearchCriteria.of("nonexistent", null, null, null, null, null, null, null, 0, 20);
        SearchResponse response = searchService.search(criteria);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0);
        assertThat(response.last()).isTrue();
    }

    // ── getSuggestions ────────────────────────────────────────

    @Test
    void getSuggestions_returnsSuggestionList() {
        when(courseIndexRepository.findTitleSuggestions("java", 5))
                .thenReturn(List.of("Java Basics", "Java Spring Boot", "Java Design Patterns"));

        SuggestionResponse response = searchService.getSuggestions("java", 5);

        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.suggestions()).contains("Java Basics", "Java Spring Boot");
    }

    @Test
    void getSuggestions_limitsToMaxTen() {
        when(courseIndexRepository.findTitleSuggestions("java", 10))
                .thenReturn(List.of("Result1"));

        // Request 100, should be capped to 10
        searchService.getSuggestions("java", 100);

        verify(courseIndexRepository).findTitleSuggestions("java", 10);
    }

    // ── Helpers ───────────────────────────────────────────────

    private CourseIndex buildCourse(String id, String title) {
        return CourseIndex.reconstitute(
                id, title, "A great course about " + title, "Short description",
                "instructor-uuid", "Jane Smith",
                "Programming", CourseLevel.INTERMEDIATE,
                new BigDecimal("39.99"), 4.2, 80, 300,
                List.of("java", "spring"),
                "English", "https://example.com/thumb.jpg",
                Instant.now().minusSeconds(86400),
                Instant.now()
        );
    }
}
