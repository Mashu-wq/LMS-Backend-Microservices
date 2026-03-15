package com.lms.courseservice.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Section — a value object within the Course aggregate.
 * Contains an ordered list of Lessons.
 */
public class Section {

    private final String sectionId;
    private String title;
    private String description;
    private int orderIndex;
    private final List<Lesson> lessons;

    private Section(String sectionId, String title, int orderIndex) {
        this.sectionId = sectionId;
        this.title = title;
        this.orderIndex = orderIndex;
        this.lessons = new ArrayList<>();
    }

    public static Section create(String title, String description, int orderIndex) {
        Section section = new Section(UUID.randomUUID().toString(), title, orderIndex);
        section.description = description;
        return section;
    }

    public static Section reconstitute(String sectionId, String title, String description,
                                        int orderIndex, List<Lesson> lessons) {
        Section section = new Section(sectionId, title, orderIndex);
        section.description = description;
        section.lessons.addAll(lessons);
        return section;
    }

    public void update(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void addLesson(Lesson lesson) {
        this.lessons.add(lesson);
    }

    public void removeLesson(String lessonId) {
        lessons.removeIf(l -> l.getLessonId().equals(lessonId));
        reorderLessons();
    }

    public int totalDurationMinutes() {
        return lessons.stream().mapToInt(Lesson::getDurationMinutes).sum();
    }

    private void reorderLessons() {
        for (int i = 0; i < lessons.size(); i++) {
            // Re-index order after removal — lessons are value objects, so we reconstitute
            Lesson old = lessons.get(i);
            lessons.set(i, Lesson.reconstitute(
                    old.getLessonId(), old.getTitle(), old.getDescription(),
                    old.getContentUrl(), old.getLessonType(), old.getDurationMinutes(),
                    i + 1, old.isPreviewEnabled()
            ));
        }
    }

    public String getSectionId() { return sectionId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getOrderIndex() { return orderIndex; }
    public List<Lesson> getLessons() { return Collections.unmodifiableList(lessons); }
    public int getLessonCount() { return lessons.size(); }
}
