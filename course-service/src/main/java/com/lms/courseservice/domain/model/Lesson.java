package com.lms.courseservice.domain.model;

import java.util.UUID;

/**
 * Lesson — a value object embedded within a Section.
 *
 * <p>Lessons are not aggregates — they have no independent lifecycle.
 * They only exist as part of their parent Section → Course aggregate.
 * Their ID is a UUID for stable references from progress-service.
 */
public class Lesson {

    private final String lessonId;
    private String title;
    private String description;
    private String contentUrl;     // S3 URL for video, or article content ID
    private LessonType lessonType;
    private int durationMinutes;
    private int orderIndex;        // Display order within section
    private boolean previewEnabled; // Free preview before enrollment

    private Lesson(String lessonId, String title, LessonType lessonType,
                   int orderIndex) {
        this.lessonId = lessonId;
        this.title = title;
        this.lessonType = lessonType;
        this.orderIndex = orderIndex;
        this.previewEnabled = false;
    }

    public static Lesson create(String title, String description, String contentUrl,
                                 LessonType lessonType, int durationMinutes, int orderIndex) {
        Lesson lesson = new Lesson(UUID.randomUUID().toString(), title, lessonType, orderIndex);
        lesson.description = description;
        lesson.contentUrl = contentUrl;
        lesson.durationMinutes = durationMinutes;
        return lesson;
    }

    public static Lesson reconstitute(String lessonId, String title, String description,
                                       String contentUrl, LessonType lessonType,
                                       int durationMinutes, int orderIndex, boolean previewEnabled) {
        Lesson lesson = new Lesson(lessonId, title, lessonType, orderIndex);
        lesson.description = description;
        lesson.contentUrl = contentUrl;
        lesson.durationMinutes = durationMinutes;
        lesson.previewEnabled = previewEnabled;
        return lesson;
    }

    public void update(String title, String description, String contentUrl, int durationMinutes) {
        this.title = title;
        this.description = description;
        this.contentUrl = contentUrl;
        this.durationMinutes = durationMinutes;
    }

    public void enablePreview() { this.previewEnabled = true; }
    public void disablePreview() { this.previewEnabled = false; }

    public String getLessonId() { return lessonId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getContentUrl() { return contentUrl; }
    public LessonType getLessonType() { return lessonType; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getOrderIndex() { return orderIndex; }
    public boolean isPreviewEnabled() { return previewEnabled; }
}
