package com.lms.searchservice.infrastructure.persistence.repository;

import com.lms.searchservice.infrastructure.persistence.document.CourseIndexDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Simple Spring Data repository for basic CRUD on CourseIndexDocument.
 *
 * <p>Complex full-text search queries are handled by
 * {@link com.lms.searchservice.infrastructure.persistence.adapter.CourseIndexRepositoryAdapter}
 * using {@code MongoTemplate} directly.
 */
public interface MongoCourseIndexCrudRepository extends MongoRepository<CourseIndexDocument, String> {
}
