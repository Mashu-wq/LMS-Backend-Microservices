package com.lms.courseservice.api.controller;

import com.lms.courseservice.application.dto.request.*;
import com.lms.courseservice.application.dto.response.*;
import com.lms.courseservice.application.service.CourseService;
import com.lms.courseservice.application.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/courses/v1")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    // ── Public endpoints (no auth required) ─────────────────

    @GetMapping
    public ResponseEntity<Page<CourseSummaryResponse>> getCatalog(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "publishedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 50),
                Sort.by(Sort.Direction.fromString(direction), sortBy));
        return ResponseEntity.ok(courseService.getPublishedCatalog(category, pageable));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseResponse> getCourse(@PathVariable String courseId) {
        return ResponseEntity.ok(courseService.getCourse(courseId));
    }

    // ── Instructor endpoints ─────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<CourseResponse> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID instructorId = UUID.fromString(jwt.getSubject());
        String instructorName = jwt.getClaimAsString("firstName") + " " + jwt.getClaimAsString("lastName");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.createCourse(request, instructorId, instructorName));
    }

    @PutMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable String courseId,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(courseService.updateCourse(
                courseId, request,
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("role")));
    }

    @PostMapping("/{courseId}/sections")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<CourseResponse> addSection(
            @PathVariable String courseId,
            @Valid @RequestBody AddSectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addSection(courseId, request,
                        UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role")));
    }

    @PostMapping("/{courseId}/sections/{sectionId}/lessons")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<CourseResponse> addLesson(
            @PathVariable String courseId,
            @PathVariable String sectionId,
            @Valid @RequestBody AddLessonRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addLesson(courseId, sectionId, request,
                        UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role")));
    }

    @DeleteMapping("/{courseId}/sections/{sectionId}/lessons/{lessonId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<CourseResponse> removeLesson(
            @PathVariable String courseId,
            @PathVariable String sectionId,
            @PathVariable String lessonId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(courseService.removeLesson(courseId, sectionId, lessonId,
                UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role")));
    }

    @PostMapping("/{courseId}/publish")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<CourseResponse> publishCourse(
            @PathVariable String courseId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(courseService.publishCourse(courseId,
                UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role")));
    }

    @PostMapping("/{courseId}/archive")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<CourseResponse> archiveCourse(
            @PathVariable String courseId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(courseService.archiveCourse(courseId,
                UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role")));
    }

    @GetMapping("/instructor/my-courses")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Page<CourseSummaryResponse>> getMyCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        UUID instructorId = UUID.fromString(jwt.getSubject());
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(courseService.getInstructorCourses(instructorId, pageable));
    }

    // ── Enrollment endpoints ─────────────────────────────────

    @PostMapping("/{courseId}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<EnrollmentResponse> enrollSelf(
            @PathVariable String courseId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID studentId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrollmentService.enroll(courseId, studentId));
    }

    @GetMapping("/my-enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Page<EnrollmentResponse>> myEnrollments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        UUID studentId = UUID.fromString(jwt.getSubject());
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enrolledAt"));
        return ResponseEntity.ok(enrollmentService.getStudentEnrollments(studentId, pageable));
    }

    @GetMapping("/{courseId}/enrollments")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Page<EnrollmentResponse>> getCourseEnrollments(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enrolledAt"));
        return ResponseEntity.ok(enrollmentService.getCourseEnrollments(courseId,
                UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"), pageable));
    }

    // ── Internal (called by other services) ─────────────────

    @GetMapping("/internal/{courseId}")
    public ResponseEntity<CourseResponse> getCourseInternal(@PathVariable String courseId) {
        return ResponseEntity.ok(courseService.getCourse(courseId));
    }

    @GetMapping("/internal/student/{studentId}/enrolled-courses")
    public ResponseEntity<java.util.List<String>> getEnrolledCourseIds(@PathVariable UUID studentId) {
        return ResponseEntity.ok(enrollmentService.getStudentEnrolledCourseIds(studentId));
    }

    @GetMapping("/internal/{courseId}/is-enrolled/{studentId}")
    public ResponseEntity<Boolean> isEnrolled(
            @PathVariable String courseId,
            @PathVariable UUID studentId) {
        return ResponseEntity.ok(enrollmentService.isStudentEnrolled(studentId, courseId));
    }
}
