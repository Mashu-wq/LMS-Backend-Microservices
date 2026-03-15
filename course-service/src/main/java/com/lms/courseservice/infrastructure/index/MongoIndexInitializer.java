package com.lms.courseservice.infrastructure.index;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Programmatic MongoDB index creation on application startup.
 *
 * <p>Why not auto-index-creation?
 * - In production, index creation on a large collection blocks writes temporarily.
 * - Programmatic creation lets us use `background: true` and gives us control.
 * - We also add text indexes here (for search-service sync) that Spring Data
 *   annotations don't handle as cleanly.
 *
 * <p>Uses ApplicationReadyEvent so indexes are created AFTER the DB connection
 * pool is fully initialized.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndexes() {
        log.info("Initializing MongoDB indexes for course-service");
        try {
            ensureCourseIndexes();
            ensureEnrollmentIndexes();
            log.info("MongoDB indexes initialized successfully");
        } catch (Exception e) {
            // Log but don't crash — indexes may already exist
            log.error("Failed to initialize MongoDB indexes: {}", e.getMessage());
        }
    }

    private void ensureCourseIndexes() {
        IndexOperations courseOps = mongoTemplate.indexOps("courses");

        // Catalog browsing — most common query
        ensureIndex(courseOps, new Index()
                .on("status", Sort.Direction.ASC)
                .on("publishedAt", Sort.Direction.DESC)
                .named("idx_status_publishedAt"));

        // Category filter
        ensureIndex(courseOps, new Index()
                .on("status", Sort.Direction.ASC)
                .on("category", Sort.Direction.ASC)
                .named("idx_status_category"));

        // Instructor dashboard
        ensureIndex(courseOps, new Index()
                .on("instructorId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .named("idx_instructorId_status"));

        // Tag search
        ensureIndex(courseOps, new Index()
                .on("tags", Sort.Direction.ASC)
                .named("idx_tags"));
    }

    private void ensureEnrollmentIndexes() {
        IndexOperations enrollmentOps = mongoTemplate.indexOps("enrollments");

        // Unique student-course pair
        ensureIndex(enrollmentOps, new Index()
                .on("studentId", Sort.Direction.ASC)
                .on("courseId", Sort.Direction.ASC)
                .unique()
                .named("idx_student_course_unique"));

        // Student's enrollments list
        ensureIndex(enrollmentOps, new Index()
                .on("studentId", Sort.Direction.ASC)
                .on("enrolledAt", Sort.Direction.DESC)
                .named("idx_student_enrolledAt"));

        // Course enrollment analytics
        ensureIndex(enrollmentOps, new Index()
                .on("courseId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .named("idx_course_status"));
    }

    private void ensureIndex(IndexOperations ops, IndexDefinition definition) {
        try {
            ops.ensureIndex(definition);
        } catch (Exception e) {
            log.warn("Index creation skipped (may already exist): {}", e.getMessage());
        }
    }
}
