package com.lms.searchservice.infrastructure.persistence.adapter;

import com.lms.searchservice.domain.model.CourseIndex;
import com.lms.searchservice.domain.model.SearchCriteria;
import com.lms.searchservice.domain.model.SortOption;
import com.lms.searchservice.domain.repository.CourseIndexRepository;
import com.lms.searchservice.infrastructure.persistence.document.CourseIndexDocument;
import com.lms.searchservice.infrastructure.persistence.mapper.CourseIndexMapper;
import com.lms.searchservice.infrastructure.persistence.repository.MongoCourseIndexCrudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CourseIndexRepositoryAdapter — MongoDB implementation of {@link CourseIndexRepository}.
 *
 * <p>Uses {@link MongoTemplate} directly for:
 * <ul>
 *   <li>Full-text search via {@code $text} operator with relevance scoring</li>
 *   <li>Filter-only browsing with B-tree index sorting (when no text query)</li>
 *   <li>Prefix-based title suggestions via regex</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseIndexRepositoryAdapter implements CourseIndexRepository {

    private final MongoCourseIndexCrudRepository crudRepository;
    private final MongoTemplate mongoTemplate;
    private final CourseIndexMapper mapper;

    @Override
    public void save(CourseIndex courseIndex) {
        crudRepository.save(mapper.toDocument(courseIndex));
    }

    @Override
    public Optional<CourseIndex> findById(String courseId) {
        return crudRepository.findById(courseId).map(mapper::toDomain);
    }

    @Override
    public void deleteById(String courseId) {
        crudRepository.deleteById(courseId);
    }

    @Override
    public long count() {
        return crudRepository.count();
    }

    // ── Full-text search ──────────────────────────────────────

    @Override
    public Page<CourseIndex> search(SearchCriteria criteria) {
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size());

        if (criteria.hasTextQuery()) {
            return textSearch(criteria, pageable);
        } else {
            return filterSearch(criteria, pageable);
        }
    }

    /**
     * Full-text search using MongoDB {@code $text} operator.
     * When sortBy is RELEVANCE, results are ranked by text score.
     * Other sort options override the text-score sort.
     */
    private Page<CourseIndex> textSearch(SearchCriteria criteria, Pageable pageable) {
        TextCriteria textCriteria = TextCriteria.forDefaultLanguage()
                .caseSensitive(false)
                .matching(criteria.query());

        // Count query (no sort, no pagination)
        Query countQuery = TextQuery.queryText(textCriteria);
        applyFilters(countQuery, criteria);
        long total = mongoTemplate.count(countQuery, CourseIndexDocument.class);

        // Data query
        Query dataQuery;
        if (criteria.sortBy() == SortOption.RELEVANCE) {
            dataQuery = TextQuery.queryText(textCriteria).sortByScore();
        } else {
            dataQuery = TextQuery.queryText(textCriteria);
            dataQuery.with(buildSort(criteria.sortBy()));
        }
        applyFilters(dataQuery, criteria);
        dataQuery.with(pageable);

        List<CourseIndex> results = mongoTemplate.find(dataQuery, CourseIndexDocument.class)
                .stream().map(mapper::toDomain).collect(Collectors.toList());

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Filter-only search (no text query) with B-tree index sort.
     */
    private Page<CourseIndex> filterSearch(SearchCriteria criteria, Pageable pageable) {
        Query query = new Query();
        applyFilters(query, criteria);

        SortOption sortBy = criteria.sortBy() == SortOption.RELEVANCE
                ? SortOption.NEWEST   // default sort when no text query
                : criteria.sortBy();
        query.with(buildSort(sortBy));

        long total = mongoTemplate.count(query, CourseIndexDocument.class);
        query.with(pageable);

        List<CourseIndex> results = mongoTemplate.find(query, CourseIndexDocument.class)
                .stream().map(mapper::toDomain).collect(Collectors.toList());

        return new PageImpl<>(results, pageable, total);
    }

    // ── Suggestions ───────────────────────────────────────────

    @Override
    public List<String> findTitleSuggestions(String prefix, int limit) {
        Pattern regex = Pattern.compile("^" + Pattern.quote(prefix), Pattern.CASE_INSENSITIVE);
        Query query = new Query(Criteria.where("title").regex(regex)).limit(limit);
        query.fields().include("title");
        return mongoTemplate.find(query, CourseIndexDocument.class)
                .stream()
                .map(CourseIndexDocument::getTitle)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────

    private void applyFilters(Query query, SearchCriteria criteria) {
        if (criteria.category() != null) {
            query.addCriteria(Criteria.where("category").is(criteria.category()));
        }
        if (criteria.level() != null) {
            query.addCriteria(Criteria.where("level").is(criteria.level().name()));
        }
        if (criteria.language() != null) {
            query.addCriteria(Criteria.where("language").is(criteria.language()));
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null) {
            query.addCriteria(Criteria.where("price")
                    .gte(criteria.minPrice()).lte(criteria.maxPrice()));
        } else if (criteria.minPrice() != null) {
            query.addCriteria(Criteria.where("price").gte(criteria.minPrice()));
        } else if (criteria.maxPrice() != null) {
            query.addCriteria(Criteria.where("price").lte(criteria.maxPrice()));
        }
        if (criteria.minRating() != null) {
            query.addCriteria(Criteria.where("averageRating").gte(criteria.minRating()));
        }
    }

    private Sort buildSort(SortOption sortBy) {
        return switch (sortBy) {
            case NEWEST    -> Sort.by(Sort.Direction.DESC, "publishedAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "price");
            case RATING    -> Sort.by(Sort.Direction.DESC, "averageRating");
            default        -> Sort.by(Sort.Direction.DESC, "publishedAt");
        };
    }
}
