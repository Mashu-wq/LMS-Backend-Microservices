package com.lms.courseservice.infrastructure.persistence.document;

import com.lms.courseservice.domain.model.CourseStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB document for Course.
 *
 * <p>The entire course hierarchy (sections + lessons) is embedded here.
 * This is the correct MongoDB pattern for data that is always read together
 * and whose cardinality is bounded (a course won't have 10,000 lessons).
 *
 * <p>Compound indexes are defined here for query performance.
 * auto-index-creation is disabled in config — MongoIndexInitializer creates them on startup.
 */
@Document(collection = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "idx_status_category", def = "{'status': 1, 'category': 1}"),
    @CompoundIndex(name = "idx_status_publishedAt", def = "{'status': 1, 'publishedAt': -1}"),
    @CompoundIndex(name = "idx_instructorId_status", def = "{'instructorId': 1, 'status': 1}")
})
public class CourseDocument {

    @Id
    private String courseId;

    private String title;
    private String description;
    private String shortDescription;

    @Indexed
    private UUID instructorId;
    private String instructorName;

    @Indexed
    private CourseStatus status;

    @Indexed
    private String category;

    private String language;
    private String level;
    private BigDecimal price;
    private String thumbnailUrl;
    private List<String> tags;

    // Embedded sections with nested lessons
    private List<SectionDocument> sections;

    private int totalEnrollments;
    private double averageRating;
    private int ratingCount;

    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant publishedAt;
}
