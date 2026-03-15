package com.lms.courseservice.domain.model;

import com.lms.courseservice.domain.exception.CourseOperationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Course — the primary aggregate root for the course domain.
 *
 * <p>Encapsulates the full course hierarchy:
 *   Course → Sections → Lessons
 *
 * <p>All mutations go through domain methods that enforce invariants:
 * - Only DRAFT courses can have sections/lessons added
 * - A course must have at least one section and one lesson to be published
 * - Status transitions are validated against the CourseStatus state machine
 * - Only the owning instructor (or an admin) can mutate — enforced at application layer
 *
 * <p>MongoDB document: the entire aggregate is stored as one document.
 * This gives us atomicity on saves without distributed transactions.
 */
public class Course {

    private final String courseId;
    private String title;
    private String description;
    private String shortDescription;
    private final UUID instructorId;
    private String instructorName;    // Denormalized for read performance
    private CourseStatus status;
    private String category;
    private String language;
    private String level;             // BEGINNER, INTERMEDIATE, ADVANCED
    private BigDecimal price;
    private String thumbnailUrl;
    private List<String> tags;
    private final List<Section> sections;
    private int totalEnrollments;
    private double averageRating;
    private int ratingCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;

    private static final int MIN_SECTIONS_TO_PUBLISH = 1;
    private static final int MIN_LESSONS_TO_PUBLISH = 1;

    // ── Construction ────────────────────────────────────────

    private Course(String courseId, String title, String description,
                   String shortDescription, UUID instructorId, String instructorName,
                   String category, String language, String level,
                   BigDecimal price, String thumbnailUrl) {
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.shortDescription = shortDescription;
        this.instructorId = instructorId;
        this.instructorName = instructorName;
        this.category = category;
        this.language = language;
        this.level = level;
        this.price = price;
        this.thumbnailUrl = thumbnailUrl;
        this.status = CourseStatus.DRAFT;
        this.sections = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.totalEnrollments = 0;
        this.averageRating = 0.0;
        this.ratingCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static Course create(String title, String description, String shortDescription,
                                 UUID instructorId, String instructorName,
                                 String category, String language, String level,
                                 BigDecimal price, String thumbnailUrl) {
        validateTitle(title);
        return new Course(UUID.randomUUID().toString(), title, description, shortDescription,
                instructorId, instructorName, category, language, level, price, thumbnailUrl);
    }

    public static Course reconstitute(String courseId, String title, String description,
                                       String shortDescription, UUID instructorId, String instructorName,
                                       CourseStatus status, String category, String language,
                                       String level, BigDecimal price, String thumbnailUrl,
                                       List<String> tags, List<Section> sections,
                                       int totalEnrollments, double averageRating, int ratingCount,
                                       Instant createdAt, Instant updatedAt, Instant publishedAt) {
        Course course = new Course(courseId, title, description, shortDescription,
                instructorId, instructorName, category, language, level, price, thumbnailUrl);
        course.status = status;
        course.tags = new ArrayList<>(tags);
        course.sections.addAll(sections);
        course.totalEnrollments = totalEnrollments;
        course.averageRating = averageRating;
        course.ratingCount = ratingCount;
        course.createdAt = createdAt;
        course.updatedAt = updatedAt;
        course.publishedAt = publishedAt;
        return course;
    }

    // ── Section Management ───────────────────────────────────

    public Section addSection(String title, String description) {
        requireDraft("add sections to");
        int orderIndex = sections.size() + 1;
        Section section = Section.create(title, description, orderIndex);
        sections.add(section);
        touch();
        return section;
    }

    public void updateSection(String sectionId, String title, String description) {
        requireDraft("update sections of");
        findSection(sectionId).update(title, description);
        touch();
    }

    public void removeSection(String sectionId) {
        requireDraft("remove sections from");
        sections.removeIf(s -> s.getSectionId().equals(sectionId));
        touch();
    }

    // ── Lesson Management ────────────────────────────────────

    public Lesson addLesson(String sectionId, String title, String description,
                             String contentUrl, LessonType lessonType,
                             int durationMinutes) {
        requireDraft("add lessons to");
        Section section = findSection(sectionId);
        int orderIndex = section.getLessonCount() + 1;
        Lesson lesson = Lesson.create(title, description, contentUrl,
                lessonType, durationMinutes, orderIndex);
        section.addLesson(lesson);
        touch();
        return lesson;
    }

    public void removeLesson(String sectionId, String lessonId) {
        requireDraft("remove lessons from");
        findSection(sectionId).removeLesson(lessonId);
        touch();
    }

    // ── Status Transitions ───────────────────────────────────

    public void publish() {
        if (!status.canTransitionTo(CourseStatus.PUBLISHED)) {
            throw new CourseOperationException(
                "Cannot publish course in status: " + status);
        }
        if (sections.size() < MIN_SECTIONS_TO_PUBLISH) {
            throw new CourseOperationException(
                "Course must have at least " + MIN_SECTIONS_TO_PUBLISH + " section to be published");
        }
        long totalLessons = sections.stream().mapToLong(Section::getLessonCount).sum();
        if (totalLessons < MIN_LESSONS_TO_PUBLISH) {
            throw new CourseOperationException(
                "Course must have at least " + MIN_LESSONS_TO_PUBLISH + " lesson to be published");
        }
        this.status = CourseStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        touch();
    }

    public void archive() {
        if (!status.canTransitionTo(CourseStatus.ARCHIVED)) {
            throw new CourseOperationException(
                "Cannot archive course in status: " + status);
        }
        this.status = CourseStatus.ARCHIVED;
        touch();
    }

    public void rePublish() {
        if (!status.canTransitionTo(CourseStatus.PUBLISHED)) {
            throw new CourseOperationException(
                "Cannot re-publish course in status: " + status);
        }
        this.status = CourseStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        touch();
    }

    // ── Course Metadata Updates ──────────────────────────────

    public void updateDetails(String title, String description, String shortDescription,
                               String category, String language, String level,
                               BigDecimal price, String thumbnailUrl, List<String> tags) {
        validateTitle(title);
        this.title = title;
        this.description = description;
        this.shortDescription = shortDescription;
        this.category = category;
        this.language = language;
        this.level = level;
        this.price = price;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = new ArrayList<>(tags);
        touch();
    }

    // ── Enrollment Stats ─────────────────────────────────────

    public void incrementEnrollments() {
        this.totalEnrollments++;
        touch();
    }

    public void updateRating(double newAverageRating, int newRatingCount) {
        this.averageRating = newAverageRating;
        this.ratingCount = newRatingCount;
        touch();
    }

    // ── Queries ──────────────────────────────────────────────

    public boolean isOwnedBy(UUID userId) {
        return this.instructorId.equals(userId);
    }

    public boolean isPublished() {
        return this.status == CourseStatus.PUBLISHED;
    }

    public int totalDurationMinutes() {
        return sections.stream().mapToInt(Section::totalDurationMinutes).sum();
    }

    public int totalLessonCount() {
        return sections.stream().mapToInt(Section::getLessonCount).sum();
    }

    // ── Helpers ──────────────────────────────────────────────

    private Section findSection(String sectionId) {
        return sections.stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new CourseOperationException("Section not found: " + sectionId));
    }

    private void requireDraft(String operation) {
        if (this.status != CourseStatus.DRAFT) {
            throw new CourseOperationException(
                "Cannot " + operation + " a course in status: " + status +
                ". Archive the course first to re-enter draft state.");
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Course title must not be blank");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("Course title must not exceed 200 characters");
        }
    }

    // ── Getters ──────────────────────────────────────────────

    public String getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getShortDescription() { return shortDescription; }
    public UUID getInstructorId() { return instructorId; }
    public String getInstructorName() { return instructorName; }
    public CourseStatus getStatus() { return status; }
    public String getCategory() { return category; }
    public String getLanguage() { return language; }
    public String getLevel() { return level; }
    public BigDecimal getPrice() { return price; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public List<String> getTags() { return Collections.unmodifiableList(tags); }
    public List<Section> getSections() { return Collections.unmodifiableList(sections); }
    public int getTotalEnrollments() { return totalEnrollments; }
    public double getAverageRating() { return averageRating; }
    public int getRatingCount() { return ratingCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
