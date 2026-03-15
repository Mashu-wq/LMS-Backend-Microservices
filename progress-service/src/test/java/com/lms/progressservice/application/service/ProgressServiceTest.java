package com.lms.progressservice.application.service;

import com.lms.progressservice.application.dto.response.CourseProgressResponse;
import com.lms.progressservice.application.port.CourseProgressEventPublisher;
import com.lms.progressservice.application.port.CourseServiceClient;
import com.lms.progressservice.domain.exception.ProgressNotFoundException;
import com.lms.progressservice.domain.model.CourseProgress;
import com.lms.progressservice.domain.model.ProgressStatus;
import com.lms.progressservice.domain.repository.CourseProgressRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock private CourseProgressRepository    progressRepository;
    @Mock private CourseProgressEventPublisher eventPublisher;
    @Mock private CourseServiceClient         courseServiceClient;

    private ProgressService progressService;

    private final UUID   studentId = UUID.randomUUID();
    private final String courseId  = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        progressService = new ProgressService(
                progressRepository, eventPublisher, courseServiceClient,
                new SimpleMeterRegistry());
    }

    // ── initializeProgress ──────────────────────────────────────────────────

    @Test
    @DisplayName("initializeProgress: creates new progress with lesson count from course-service")
    void initializeProgress_createsRecord() {
        given(progressRepository.existsByStudentIdAndCourseId(studentId, courseId)).willReturn(false);
        given(courseServiceClient.getTotalLessons(courseId)).willReturn(Optional.of(5));
        given(progressRepository.save(any(CourseProgress.class)))
                .willAnswer(inv -> inv.getArgument(0));

        progressService.initializeProgress(studentId, courseId, "Java Basics");

        verify(progressRepository).save(argThat(p ->
                p.getStudentId().equals(studentId)
                && p.getCourseId().equals(courseId)
                && p.getTotalLessons() == 5));
    }

    @Test
    @DisplayName("initializeProgress: idempotent — skips if already exists")
    void initializeProgress_idempotent() {
        given(progressRepository.existsByStudentIdAndCourseId(studentId, courseId)).willReturn(true);

        progressService.initializeProgress(studentId, courseId, "Java Basics");

        verify(progressRepository, never()).save(any());
    }

    @Test
    @DisplayName("initializeProgress: totalLessons=0 when course-service unavailable")
    void initializeProgress_courseServiceUnavailable() {
        given(progressRepository.existsByStudentIdAndCourseId(studentId, courseId)).willReturn(false);
        given(courseServiceClient.getTotalLessons(courseId)).willReturn(Optional.empty());
        given(progressRepository.save(any(CourseProgress.class)))
                .willAnswer(inv -> inv.getArgument(0));

        progressService.initializeProgress(studentId, courseId, "Java Basics");

        verify(progressRepository).save(argThat(p -> p.getTotalLessons() == 0));
    }

    // ── markLessonComplete ──────────────────────────────────────────────────

    @Test
    @DisplayName("markLessonComplete: new lesson → progress updated, no event")
    void markLessonComplete_notFinished() {
        CourseProgress progress = CourseProgress.create(studentId, courseId, "Java Basics", 3);
        given(progressRepository.findByStudentIdAndCourseId(studentId, courseId))
                .willReturn(Optional.of(progress));
        given(progressRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CourseProgressResponse response =
                progressService.markLessonComplete(studentId, courseId, "lesson-1");

        assertThat(response.completedLessons()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(ProgressStatus.IN_PROGRESS);
        verify(eventPublisher, never()).publishCourseCompleted(any());
    }

    @Test
    @DisplayName("markLessonComplete: last lesson → COMPLETED + event published")
    void markLessonComplete_courseCompleted() {
        CourseProgress progress = CourseProgress.create(studentId, courseId, "Java Basics", 1);
        given(progressRepository.findByStudentIdAndCourseId(studentId, courseId))
                .willReturn(Optional.of(progress));
        given(progressRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CourseProgressResponse response =
                progressService.markLessonComplete(studentId, courseId, "lesson-1");

        assertThat(response.status()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(response.completionPercentage()).isEqualTo(100);
        verify(eventPublisher).publishCourseCompleted(any(CourseProgress.class));
    }

    @Test
    @DisplayName("markLessonComplete: idempotent — already done lesson → no duplicate event")
    void markLessonComplete_alreadyDone_noEvent() {
        CourseProgress progress = CourseProgress.create(studentId, courseId, "Java Basics", 2);
        progress.completeLesson("lesson-1");

        given(progressRepository.findByStudentIdAndCourseId(studentId, courseId))
                .willReturn(Optional.of(progress));
        given(progressRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        progressService.markLessonComplete(studentId, courseId, "lesson-1");

        verify(eventPublisher, never()).publishCourseCompleted(any());
    }

    @Test
    @DisplayName("markLessonComplete: no progress record → ProgressNotFoundException")
    void markLessonComplete_notFound() {
        given(progressRepository.findByStudentIdAndCourseId(studentId, courseId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> progressService.markLessonComplete(studentId, courseId, "l1"))
                .isInstanceOf(ProgressNotFoundException.class);
    }

    // ── getCourseProgress ───────────────────────────────────────────────────

    @Test
    @DisplayName("getCourseProgress: not found → ProgressNotFoundException")
    void getCourseProgress_notFound() {
        given(progressRepository.findByStudentIdAndCourseId(studentId, courseId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> progressService.getCourseProgress(studentId, courseId))
                .isInstanceOf(ProgressNotFoundException.class);
    }
}
