package com.lms.progressservice.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * CourseProgress aggregate root.
 *
 * <p>One record exists per (student, course) pair. The aggregate owns the set
 * of completed lesson IDs and enforces all state transitions: a course moves
 * to COMPLETED exactly once, when every lesson has been marked done.
 *
 * <p>Lesson completion is idempotent — calling {@link #completeLesson} with
 * an already-completed lessonId is a no-op and does not raise an error.
 */
@Getter
public class CourseProgress {

    private final UUID          progressId;
    private final UUID          studentId;
    private final String        courseId;
    private final String        courseTitle;
    private       int           totalLessons;
    private final Set<String>   completedLessonIds;
    private       ProgressStatus status;
    private final Instant       enrolledAt;
    private       Instant       completedAt;
    private       Instant       lastActivityAt;

    private CourseProgress(UUID progressId, UUID studentId, String courseId,
                           String courseTitle, int totalLessons,
                           Set<String> completedLessonIds, ProgressStatus status,
                           Instant enrolledAt, Instant completedAt,
                           Instant lastActivityAt) {
        this.progressId         = progressId;
        this.studentId          = studentId;
        this.courseId           = courseId;
        this.courseTitle        = courseTitle;
        this.totalLessons       = totalLessons;
        this.completedLessonIds = new HashSet<>(completedLessonIds);
        this.status             = status;
        this.enrolledAt         = enrolledAt;
        this.completedAt        = completedAt;
        this.lastActivityAt     = lastActivityAt;
    }

    // ── Factory: new enrolment ──────────────────────────────────────────────

    public static CourseProgress create(UUID studentId, String courseId,
                                        String courseTitle, int totalLessons) {
        Instant now = Instant.now();
        return new CourseProgress(
                UUID.randomUUID(), studentId, courseId, courseTitle,
                totalLessons, Set.of(),
                ProgressStatus.IN_PROGRESS,
                now, null, now);
    }

    // ── Factory: rehydrate from persistence ────────────────────────────────

    public static CourseProgress reconstitute(UUID progressId, UUID studentId,
                                               String courseId, String courseTitle,
                                               int totalLessons,
                                               Set<String> completedLessonIds,
                                               ProgressStatus status,
                                               Instant enrolledAt, Instant completedAt,
                                               Instant lastActivityAt) {
        return new CourseProgress(progressId, studentId, courseId, courseTitle,
                totalLessons, completedLessonIds, status,
                enrolledAt, completedAt, lastActivityAt);
    }

    // ── Business logic ──────────────────────────────────────────────────────

    /**
     * Marks a lesson as completed. Idempotent — safe to call multiple times
     * for the same lessonId without side effects.
     *
     * @return {@code true} if this call newly completed the lesson (first time),
     *         {@code false} if the lesson was already marked done.
     */
    public boolean completeLesson(String lessonId) {
        if (completedLessonIds.contains(lessonId)) {
            return false;   // already done — idempotent, no state change
        }
        completedLessonIds.add(lessonId);
        lastActivityAt = Instant.now();
        checkCompletion();
        return true;
    }

    /**
     * Updates the total lesson count (e.g., when the course adds lessons after
     * a student enrolls). Re-evaluates completion in case the student had already
     * finished all previous lessons.
     */
    public void updateTotalLessons(int total) {
        this.totalLessons = total;
        checkCompletion();
    }

    public boolean isLessonCompleted(String lessonId) {
        return completedLessonIds.contains(lessonId);
    }

    /**
     * Completion percentage as an integer 0-100.
     * Returns 0 if totalLessons is not yet known (0 sentinel value).
     */
    public int completionPercentage() {
        if (totalLessons == 0) return 0;
        return (int) Math.round((double) completedLessonIds.size() / totalLessons * 100.0);
    }

    public Set<String> getCompletedLessonIds() {
        return Collections.unmodifiableSet(completedLessonIds);
    }

    public boolean isCompleted() {
        return status == ProgressStatus.COMPLETED;
    }

    // ── Private ─────────────────────────────────────────────────────────────

    private void checkCompletion() {
        if (status != ProgressStatus.COMPLETED
                && totalLessons > 0
                && completedLessonIds.size() >= totalLessons) {
            status      = ProgressStatus.COMPLETED;
            completedAt = Instant.now();
        }
    }
}
