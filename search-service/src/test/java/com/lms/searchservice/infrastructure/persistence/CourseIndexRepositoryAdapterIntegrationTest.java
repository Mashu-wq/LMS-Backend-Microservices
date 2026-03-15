package com.lms.searchservice.infrastructure.persistence;

import com.lms.searchservice.domain.model.CourseIndex;
import com.lms.searchservice.domain.model.CourseLevel;
import com.lms.searchservice.domain.model.SearchCriteria;
import com.lms.searchservice.domain.model.SortOption;
import com.lms.searchservice.domain.repository.CourseIndexRepository;
import com.lms.searchservice.infrastructure.index.MongoIndexInitializer;
import com.lms.searchservice.infrastructure.persistence.adapter.CourseIndexRepositoryAdapter;
import com.lms.searchservice.infrastructure.persistence.mapper.CourseIndexMapper;
import com.lms.searchservice.infrastructure.persistence.repository.MongoCourseIndexCrudRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for CourseIndexRepositoryAdapter using a real MongoDB instance (Testcontainers).
 *
 * <p>Tests full-text search, filter queries, and suggestion matching.
 * Note: MongoDB full-text search requires a text index; the {@link MongoIndexInitializer}
 * is imported to ensure indexes exist before tests run.
 */
@DataMongoTest
@Testcontainers
@Import({CourseIndexRepositoryAdapter.class, CourseIndexMapper.class, MongoIndexInitializer.class})
class CourseIndexRepositoryAdapterIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void setMongoUri(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private CourseIndexRepository repository;

    @Autowired
    private MongoCourseIndexCrudRepository crudRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        crudRepository.deleteAll();
    }

    // ── Indexing ──────────────────────────────────────────────

    @Test
    void save_thenFindById_returnsIndexedCourse() {
        CourseIndex course = buildCourse("c1", "Java Spring Boot Masterclass",
                "Learn Spring Boot from scratch", "Programming", CourseLevel.INTERMEDIATE,
                new BigDecimal("49.99"), "English");

        repository.save(course);

        Optional<CourseIndex> found = repository.findById("c1");
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Java Spring Boot Masterclass");
        assertThat(found.get().level()).isEqualTo(CourseLevel.INTERMEDIATE);
    }

    @Test
    void deleteById_removesFromIndex() {
        CourseIndex course = buildCourse("c2", "Python for Data Science",
                "Complete Python course", "Data Science", CourseLevel.BEGINNER,
                new BigDecimal("29.99"), "English");
        repository.save(course);

        repository.deleteById("c2");

        assertThat(repository.findById("c2")).isEmpty();
    }

    // ── Filter Search (no text query) ─────────────────────────

    @Test
    void filterSearch_byCategory_returnsMatchingCourses() {
        repository.save(buildCourse("c3", "Java Fundamentals", "Basics", "Programming",
                CourseLevel.BEGINNER, new BigDecimal("19.99"), "English"));
        repository.save(buildCourse("c4", "Photoshop Essentials", "Design basics", "Design",
                CourseLevel.BEGINNER, new BigDecimal("24.99"), "English"));

        SearchCriteria criteria = SearchCriteria.of(
                null, "Programming", null, null, null, null, null, "NEWEST", 0, 10);
        Page<CourseIndex> results = repository.search(criteria);

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).courseId()).isEqualTo("c3");
    }

    @Test
    void filterSearch_byPriceRange_returnsMatchingCourses() {
        repository.save(buildCourse("c5", "Free Python Basics", "Free course", "Programming",
                CourseLevel.BEGINNER, BigDecimal.ZERO, "English"));
        repository.save(buildCourse("c6", "Advanced Python", "Paid course", "Programming",
                CourseLevel.ADVANCED, new BigDecimal("99.99"), "English"));

        SearchCriteria criteria = SearchCriteria.of(
                null, null, null,
                new BigDecimal("1.00"), new BigDecimal("50.00"),
                null, null, "PRICE_ASC", 0, 10);
        Page<CourseIndex> results = repository.search(criteria);

        assertThat(results.getTotalElements()).isEqualTo(0); // none in [1.00, 50.00]
    }

    @Test
    void filterSearch_byLevel_returnsMatchingCourses() {
        repository.save(buildCourse("c7", "Beginner Java", "Start here", "Programming",
                CourseLevel.BEGINNER, new BigDecimal("15.00"), "English"));
        repository.save(buildCourse("c8", "Advanced Java", "Expert level", "Programming",
                CourseLevel.ADVANCED, new BigDecimal("89.00"), "English"));

        SearchCriteria criteria = SearchCriteria.of(
                null, null, "BEGINNER", null, null, null, null, "NEWEST", 0, 10);
        Page<CourseIndex> results = repository.search(criteria);

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).courseId()).isEqualTo("c7");
    }

    @Test
    void filterSearch_pagination_returnsCorrectPage() {
        for (int i = 0; i < 5; i++) {
            repository.save(buildCourse("c" + (10 + i), "Course " + i,
                    "Description", "Programming", CourseLevel.INTERMEDIATE,
                    new BigDecimal("10.00"), "English"));
        }

        SearchCriteria page0 = SearchCriteria.of(null, null, null, null, null, null, null, "NEWEST", 0, 2);
        SearchCriteria page1 = SearchCriteria.of(null, null, null, null, null, null, null, "NEWEST", 1, 2);

        Page<CourseIndex> firstPage  = repository.search(page0);
        Page<CourseIndex> secondPage = repository.search(page1);

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(firstPage.isLast()).isFalse();
    }

    // ── Suggestions ───────────────────────────────────────────

    @Test
    void findTitleSuggestions_prefixMatch_returnsSuggestions() {
        repository.save(buildCourse("s1", "Java Programming", "Java course",
                "Programming", CourseLevel.BEGINNER, new BigDecimal("9.99"), "English"));
        repository.save(buildCourse("s2", "JavaScript Fundamentals", "JS course",
                "Programming", CourseLevel.BEGINNER, new BigDecimal("9.99"), "English"));
        repository.save(buildCourse("s3", "Python Basics", "Python course",
                "Programming", CourseLevel.BEGINNER, new BigDecimal("9.99"), "English"));

        List<String> suggestions = repository.findTitleSuggestions("Jav", 5);

        assertThat(suggestions).hasSize(2);
        assertThat(suggestions).allMatch(s -> s.toLowerCase().startsWith("jav"));
    }

    @Test
    void findTitleSuggestions_caseInsensitive_returnsResults() {
        repository.save(buildCourse("s4", "Spring Boot Deep Dive", "Spring course",
                "Programming", CourseLevel.ADVANCED, new BigDecimal("79.99"), "English"));

        List<String> suggestions = repository.findTitleSuggestions("spring", 5);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0)).isEqualTo("Spring Boot Deep Dive");
    }

    // ── Helpers ───────────────────────────────────────────────

    private CourseIndex buildCourse(String id, String title, String description,
                                     String category, CourseLevel level,
                                     BigDecimal price, String language) {
        return CourseIndex.reconstitute(
                id, title, description, null,
                "instructor-1", "John Doe",
                category, level, price,
                4.5, 100, 500,
                List.of("tag1", "tag2"),
                language, null,
                Instant.now().minusSeconds(3600),
                Instant.now()
        );
    }
}
