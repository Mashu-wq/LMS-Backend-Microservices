package com.lms.searchservice.infrastructure.index;

import com.lms.searchservice.infrastructure.persistence.document.CourseIndexDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

/**
 * MongoIndexInitializer — creates MongoDB indexes on startup.
 *
 * <p>Runs once at application start via {@link ApplicationRunner}.
 * All index creation is idempotent (ensureIndex is a no-op if the index already exists).
 *
 * <p>Text index weights:
 * <ul>
 *   <li>title ×3 — highest signal for relevance</li>
 *   <li>description ×2 — strong signal</li>
 *   <li>instructorName ×2 — users search by instructor name</li>
 *   <li>category ×1 — lower weight, exact filter preferred</li>
 *   <li>tags ×1 — keyword matching</li>
 * </ul>
 *
 * <p>MongoDB allows only ONE text index per collection. All text fields must
 * be declared in a single compound text index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing MongoDB indexes for course_index collection");
        IndexOperations indexOps = mongoTemplate.indexOps(CourseIndexDocument.class);

        // Single compound text index (MongoDB limit: one per collection)
        TextIndexDefinition textIndex = TextIndexDefinition.builder()
                .onField("title",         3f)
                .onField("description",   2f)
                .onField("instructorName",2f)
                .onField("category",      1f)
                .onField("tags",          1f)
                .build();
        indexOps.ensureIndex(textIndex);
        log.info("Text index created/verified: title×3, description×2, instructorName×2, category×1, tags×1");

        // B-tree indexes for filter and sort operations
        indexOps.ensureIndex(new Index().on("category",      Sort.Direction.ASC));
        indexOps.ensureIndex(new Index().on("level",         Sort.Direction.ASC));
        indexOps.ensureIndex(new Index().on("language",      Sort.Direction.ASC));
        indexOps.ensureIndex(new Index().on("price",         Sort.Direction.ASC));
        indexOps.ensureIndex(new Index().on("averageRating", Sort.Direction.DESC));
        indexOps.ensureIndex(new Index().on("publishedAt",   Sort.Direction.DESC));
        indexOps.ensureIndex(new Index().on("instructorId",  Sort.Direction.ASC));

        log.info("All MongoDB indexes initialized for course_index collection");
    }
}
