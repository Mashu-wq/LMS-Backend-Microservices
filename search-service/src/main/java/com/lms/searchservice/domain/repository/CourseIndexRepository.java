package com.lms.searchservice.domain.repository;

import com.lms.searchservice.domain.model.CourseIndex;
import com.lms.searchservice.domain.model.SearchCriteria;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

/**
 * CourseIndexRepository — domain-owned port for search index persistence.
 *
 * <p>The implementation lives in the infrastructure layer (MongoDB adapter).
 * Search uses MongoDB's native full-text search ($text operator) for relevance-ranked results.
 */
public interface CourseIndexRepository {

    void save(CourseIndex courseIndex);

    Optional<CourseIndex> findById(String courseId);

    void deleteById(String courseId);

    Page<CourseIndex> search(SearchCriteria criteria);

    List<String> findTitleSuggestions(String prefix, int limit);

    long count();
}
