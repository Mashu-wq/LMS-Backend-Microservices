package com.lms.searchservice.infrastructure.persistence.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * CourseIndexDocument — MongoDB persistence model for the search index.
 *
 * <p>Stored in the {@code course_index} collection of {@code lms_search} database.
 *
 * <p>Text index (title ×3, description ×2, instructorName ×2, category ×1, tags ×1)
 * is created by {@link com.lms.searchservice.infrastructure.index.MongoIndexInitializer}.
 * MongoDB allows only ONE text index per collection.
 *
 * <p>Additional B-tree indexes on category, level, price, averageRating, publishedAt
 * support filtered browsing and sorting when no full-text query is present.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "course_index")
public class CourseIndexDocument {

    @Id
    private String courseId;

    private String title;
    private String description;
    private String shortDescription;
    private String instructorId;
    private String instructorName;
    private String category;

    /** Stored as string ("BEGINNER", "INTERMEDIATE", "ADVANCED", "ALL_LEVELS"). */
    private String level;

    private BigDecimal price;
    private double averageRating;
    private int ratingCount;
    private int totalEnrollments;
    private List<String> tags;
    private String language;
    private String thumbnailUrl;
    private Instant publishedAt;
    private Instant indexedAt;

    @LastModifiedDate
    private Instant updatedAt;
}
