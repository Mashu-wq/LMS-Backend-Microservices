package com.lms.courseservice.application.service;

import com.lms.courseservice.application.dto.response.EnrollmentResponse;
import com.lms.courseservice.application.port.CourseEventPublisher;
import com.lms.courseservice.domain.exception.CourseNotFoundException;
import com.lms.courseservice.domain.exception.CourseOperationException;
import com.lms.courseservice.domain.exception.EnrollmentException;
import com.lms.courseservice.domain.model.Course;
import com.lms.courseservice.domain.model.Enrollment;
import com.lms.courseservice.domain.repository.CourseRepository;
import com.lms.courseservice.domain.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CourseEventPublisher eventPublisher;
    private final CourseService courseService;

    /**
     * Enroll a student in a course.
     *
     * <p>Called after payment-service confirms a successful payment.
     * In a free course scenario, this can be called directly.
     *
     * <p>Idempotent — if the student is already enrolled, returns the existing enrollment.
     */
    public EnrollmentResponse enroll(String courseId, UUID studentId) {
        log.info("Enrolling studentId={} in courseId={}", studentId, courseId);

        // Idempotency check
        if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            log.warn("Student {} already enrolled in course {} — returning existing", studentId, courseId);
            return enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)
                    .map(EnrollmentResponse::from)
                    .orElseThrow(() -> new EnrollmentException("Enrollment inconsistency for student " + studentId));
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        if (!course.isPublished()) {
            throw new CourseOperationException("Cannot enroll in an unpublished course: " + courseId);
        }

        Enrollment enrollment = Enrollment.create(
                courseId, studentId, course.getTitle(), course.getInstructorId());

        Enrollment saved = enrollmentRepository.save(enrollment);

        // Update enrollment count on course document
        courseService.incrementEnrollmentCount(courseId);

        // Publish event for notification-service
        eventPublisher.publishStudentEnrolled(saved);

        log.info("Enrollment created enrollmentId={}", saved.getEnrollmentId());
        return EnrollmentResponse.from(saved);
    }

    public Page<EnrollmentResponse> getStudentEnrollments(UUID studentId, Pageable pageable) {
        return enrollmentRepository.findByStudentId(studentId, pageable)
                .map(EnrollmentResponse::from);
    }

    public Page<EnrollmentResponse> getCourseEnrollments(String courseId,
                                                          UUID requestingUserId, String role,
                                                          Pageable pageable) {
        // Only the course instructor or admin can see enrollment list
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        if (!"ADMIN".equals(role) && !course.isOwnedBy(requestingUserId)) {
            throw new com.lms.courseservice.domain.exception.UnauthorizedCourseAccessException(
                "Not authorized to view enrollments for course " + courseId);
        }

        return enrollmentRepository.findByCourseId(courseId, pageable)
                .map(EnrollmentResponse::from);
    }

    public List<String> getStudentEnrolledCourseIds(UUID studentId) {
        return enrollmentRepository.findCourseIdsByStudentId(studentId);
    }

    public boolean isStudentEnrolled(UUID studentId, String courseId) {
        return enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId);
    }
}
