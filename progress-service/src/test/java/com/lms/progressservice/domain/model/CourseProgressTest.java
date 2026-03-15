package com.lms.progressservice.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourseProgressTest {

    private final UUID   studentId = UUID.randomUUID();
    private final String courseId  = UUID.randomUUID().toString();

    @Test
    @DisplayName("create: starts at 0% IN_PROGRESS")
    void create_initialState() {
        CourseProgress p = CourseProgress.create(studentId, courseId, "Java Basics", 5);

        assertThat(p.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(p.completionPercentage()).isZero();
        assertThat(p.getCompletedLessonIds()).isEmpty();
        assertThat(p.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("completeLesson: increments percentage correctly")
    void completeLesson_incrementsPercentage() {
        CourseProgress p = CourseProgress.create(studentId, courseId, "Java Basics", 4);

        p.completeLesson("lesson-1");
        assertThat(p.completionPercentage()).isEqualTo(25);

        p.completeLesson("lesson-2");
        assertThat(p.completionPercentage()).isEqualTo(50);
    }

    @Test
    @DisplayName("completeLesson: all lessons done → status becomes COMPLETED")
    void completeLesson_allDone_statusCompleted() {
        CourseProgress p = CourseProgress.create(studentId, courseId, "Java Basics", 2);

        p.completeLesson("lesson-1");
        assertThat(p.isCompleted()).isFalse();

        p.completeLesson("lesson-2");
        assertThat(p.isCompleted()).isTrue();
        assertThat(p.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(p.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("completeLesson: idempotent — second call returns false, no state change")
    void completeLesson_idempotent() {
        CourseProgress p = CourseProgress.create(studentId, courseId, "Java Basics", 5);

        boolean firstCall  = p.completeLesson("lesson-1");
        boolean secondCall = p.completeLesson("lesson-1");

        assertThat(firstCall).isTrue();
        assertThat(secondCall).isFalse();
        assertThat(p.getCompletedLessonIds()).hasSize(1);
    }

    @Test
    @DisplayName("completionPercentage: returns 0 when totalLessons is 0 (not yet fetched)")
    void completionPercentage_zeroWhenTotalUnknown() {
        CourseProgress p = CourseProgress.create(studentId, courseId, "Java Basics", 0);
        assertThat(p.completionPercentage()).isZero();
    }

    @Test
    @DisplayName("updateTotalLessons: triggers completion check if already done")
    void updateTotalLessons_triggersCompletion() {
        // Start with totalLessons=0 (course-service unavailable at enrollment)
        CourseProgress p = CourseProgress.create(studentId, courseId, "Java Basics", 0);
        p.completeLesson("lesson-1");
        assertThat(p.isCompleted()).isFalse();  // 0 total → can't complete

        // Now course-service updates the count to 1
        p.updateTotalLessons(1);
        assertThat(p.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("reconstitute: restores all state from persistence")
    void reconstitute_restoresState() {
        var now = java.time.Instant.now();
        CourseProgress p = CourseProgress.reconstitute(
                UUID.randomUUID(), studentId, courseId, "Java Basics",
                3, java.util.Set.of("l1", "l2"),
                ProgressStatus.IN_PROGRESS,
                now, null, now);

        assertThat(p.getCompletedLessonIds()).containsExactlyInAnyOrder("l1", "l2");
        assertThat(p.completionPercentage()).isEqualTo(67);
    }
}
