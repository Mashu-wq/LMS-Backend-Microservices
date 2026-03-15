package com.lms.progressservice.application.service;

import com.lms.progressservice.application.dto.response.CourseProgressResponse;
import com.lms.progressservice.application.dto.response.PageResponse;
import com.lms.progressservice.application.port.CourseProgressEventPublisher;
import com.lms.progressservice.application.port.CourseServiceClient;
import com.lms.progressservice.domain.exception.ProgressNotFoundException;
import com.lms.progressservice.domain.model.CourseProgress;
import com.lms.progressservice.domain.repository.CourseProgressRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class ProgressService {

    private final CourseProgressRepository   progressRepository;
    private final CourseProgressEventPublisher eventPublisher;
    private final CourseServiceClient         courseServiceClient;

    private final Counter lessonsCompletedCounter;
    private final Counter coursesCompletedCounter;
    private final Counter progressInitializedCounter;

    public ProgressService(CourseProgressRepository progressRepository,
                           CourseProgressEventPublisher eventPublisher,
                           CourseServiceClient courseServiceClient,
                           MeterRegistry meterRegistry) {
        this.progressRepository   = progressRepository;
        this.eventPublisher       = eventPublisher;
        this.courseServiceClient  = courseServiceClient;

        this.lessonsCompletedCounter = Counter.builder("progress.lessons.completed")
                .description("Total lessons marked as completed")
                .register(meterRegistry);
        this.coursesCompletedCounter = Counter.builder("progress.courses.completed")
                .description("Total courses completed by students")
                .register(meterRegistry);
        this.progressInitializedCounter = Counter.builder("progress.initialized")
                .description("Total progress records created (enrolments tracked)")
                .register(meterRegistry);
    }

    // ── Initialise on enrolment ─────────────────────────────────────────────

    /**
     * Called by the StudentEnrolledEvent consumer.
     * Idempotent — safe to call multiple times for the same (student, course) pair.
     */
    @Transactional
    public void initializeProgress(UUID studentId, String courseId, String courseTitle) {
        if (progressRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            log.info("Progress already exists for student={} course={} — skipping init",
                    studentId, courseId);
            return;
        }

        // Attempt to fetch total lesson count from course-service (non-critical)
        int totalLessons = courseServiceClient.getTotalLessons(courseId).orElse(0);

        CourseProgress progress = CourseProgress.create(
                studentId, courseId, courseTitle, totalLessons);
        progressRepository.save(progress);
        progressInitializedCounter.increment();

        log.info("Progress initialised — student={} course={} totalLessons={}",
                studentId, courseId, totalLessons);
    }

    // ── Mark lesson complete ────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "course-progress", key = "#studentId + ':' + #courseId")
    public CourseProgressResponse markLessonComplete(UUID studentId, String courseId,
                                                      String lessonId) {
        CourseProgress progress = progressRepository
                .findByStudentIdAndCourseId(studentId, courseId)
                .orElseThrow(() -> new ProgressNotFoundException(studentId, courseId));

        boolean newlyCompleted = progress.completeLesson(lessonId);

        if (newlyCompleted) {
            lessonsCompletedCounter.increment();
            log.info("Lesson {} completed by student={} in course={} — {}%",
                    lessonId, studentId, courseId, progress.completionPercentage());
        }

        // If the lesson completion tipped the course to COMPLETED, publish event
        if (newlyCompleted && progress.isCompleted()) {
            coursesCompletedCounter.increment();
            eventPublisher.publishCourseCompleted(progress);
            log.info("Course {} COMPLETED by student={}", courseId, studentId);
        }

        progressRepository.save(progress);
        return CourseProgressResponse.from(progress);
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    @Cacheable(value = "course-progress", key = "#studentId + ':' + #courseId")
    public CourseProgressResponse getCourseProgress(UUID studentId, String courseId) {
        return progressRepository.findByStudentIdAndCourseId(studentId, courseId)
                .map(CourseProgressResponse::from)
                .orElseThrow(() -> new ProgressNotFoundException(studentId, courseId));
    }

    public PageResponse<CourseProgressResponse> getStudentProgress(UUID studentId,
                                                                     Pageable pageable) {
        return PageResponse.from(
                progressRepository.findByStudentId(studentId, pageable),
                CourseProgressResponse::from);
    }

    /**
     * Instructor / admin view: progress of all students in a course.
     */
    public PageResponse<CourseProgressResponse> getCourseProgressReport(String courseId,
                                                                          Pageable pageable) {
        return PageResponse.from(
                progressRepository.findByCourseId(courseId, pageable),
                CourseProgressResponse::from);
    }
}
