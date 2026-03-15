package com.lms.progressservice.api.controller;

import com.lms.progressservice.application.dto.response.CourseProgressResponse;
import com.lms.progressservice.application.dto.response.PageResponse;
import com.lms.progressservice.application.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/progress/v1")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    /**
     * List authenticated student's progress across all enrolled courses.
     */
    @GetMapping("/my-courses")
    public ResponseEntity<PageResponse<CourseProgressResponse>> getMyProgress(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID studentId = UUID.fromString(jwt.getSubject());
        PageRequest pageable = PageRequest.of(page, size, Sort.by("lastActivityAt").descending());
        return ResponseEntity.ok(progressService.getStudentProgress(studentId, pageable));
    }

    /**
     * Get the authenticated student's progress in a specific course.
     */
    @GetMapping("/courses/{courseId}")
    public ResponseEntity<CourseProgressResponse> getCourseProgress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId) {

        UUID studentId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(progressService.getCourseProgress(studentId, courseId));
    }

    /**
     * Mark a lesson as completed for the authenticated student.
     * Idempotent — calling this for an already-completed lesson returns 200 with
     * the current state without making changes.
     */
    @PostMapping("/courses/{courseId}/lessons/{lessonId}/complete")
    public ResponseEntity<CourseProgressResponse> markLessonComplete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseId,
            @PathVariable String lessonId) {

        UUID studentId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(progressService.markLessonComplete(studentId, courseId, lessonId));
    }

    /**
     * Instructor / admin: view progress of all students in a course.
     */
    @GetMapping("/admin/courses/{courseId}/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<PageResponse<CourseProgressResponse>> getCourseProgressReport(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("lastActivityAt").descending());
        return ResponseEntity.ok(progressService.getCourseProgressReport(courseId, pageable));
    }

    /**
     * Internal endpoint — not exposed via gateway.
     * Allows course-service and other services to check a student's progress.
     */
    @GetMapping("/internal/courses/{courseId}/students/{studentId}")
    public ResponseEntity<CourseProgressResponse> getProgressInternal(
            @PathVariable String courseId,
            @PathVariable UUID studentId) {

        return ResponseEntity.ok(progressService.getCourseProgress(studentId, courseId));
    }
}
