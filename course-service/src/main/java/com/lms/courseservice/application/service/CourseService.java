package com.lms.courseservice.application.service;

import com.lms.courseservice.application.dto.request.*;
import com.lms.courseservice.application.dto.response.*;
import com.lms.courseservice.application.port.CourseEventPublisher;
import com.lms.courseservice.domain.exception.CourseNotFoundException;
import com.lms.courseservice.domain.exception.UnauthorizedCourseAccessException;
import com.lms.courseservice.domain.model.*;
import com.lms.courseservice.domain.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Course Application Service.
 *
 * <p>Cache strategy:
 * - Individual published courses: cached by courseId (5 min TTL).
 * - Published catalog (paginated): NOT cached at this layer — the gateway's
 *   Redis rate limiter absorbs burst traffic, and search-service handles heavy catalog reads.
 * - On publish/archive/update: evict the specific course cache entry.
 *
 * <p>Authorization: the service receives the authenticated userId and role.
 * Ownership checks (instructor can only modify their own courses) are enforced here.
 * The controller extracts these from the JWT and passes them in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private static final String COURSE_CACHE = "courses";

    private final CourseRepository courseRepository;
    private final CourseEventPublisher eventPublisher;

    // ── Create ───────────────────────────────────────────────

    public CourseResponse createCourse(CreateCourseRequest request,
                                        UUID instructorId, String instructorName) {
        log.info("Creating course title='{}' instructorId={}", request.title(), instructorId);

        List<String> tags = request.tags() != null ? request.tags() : List.of();

        Course course = Course.create(
                request.title(), request.description(), request.shortDescription(),
                instructorId, instructorName, request.category(), request.language(),
                request.level(), request.price(), request.thumbnailUrl()
        );

        // Add tags if provided
        if (!tags.isEmpty()) {
            course.updateDetails(course.getTitle(), course.getDescription(),
                    course.getShortDescription(), course.getCategory(),
                    course.getLanguage(), course.getLevel(), course.getPrice(),
                    course.getThumbnailUrl(), tags);
        }

        Course saved = courseRepository.save(course);
        log.info("Course created courseId={}", saved.getCourseId());
        return CourseResponse.from(saved);
    }

    // ── Read ─────────────────────────────────────────────────

    @Cacheable(value = COURSE_CACHE, key = "#courseId")
    public CourseResponse getCourse(String courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        return CourseResponse.from(course);
    }

    public Page<CourseSummaryResponse> getPublishedCatalog(String category, Pageable pageable) {
        Page<Course> courses = (category != null && !category.isBlank())
                ? courseRepository.findByStatusAndCategory(CourseStatus.PUBLISHED, category, pageable)
                : courseRepository.findByStatus(CourseStatus.PUBLISHED, pageable);
        return courses.map(CourseSummaryResponse::from);
    }

    public Page<CourseSummaryResponse> getInstructorCourses(UUID instructorId, Pageable pageable) {
        return courseRepository.findByInstructorId(instructorId, pageable)
                .map(CourseSummaryResponse::from);
    }

    // ── Update ───────────────────────────────────────────────

    @Caching(evict = {
        @CacheEvict(value = COURSE_CACHE, key = "#courseId")
    })
    public CourseResponse updateCourse(String courseId, UpdateCourseRequest request,
                                        UUID requestingUserId, String requestingRole) {
        Course course = loadAndAuthorize(courseId, requestingUserId, requestingRole);

        List<String> tags = request.tags() != null ? request.tags() : course.getTags();

        course.updateDetails(
                request.title(), request.description(), request.shortDescription(),
                request.category(), request.language(), request.level(),
                request.price(), request.thumbnailUrl(), tags
        );

        return CourseResponse.from(courseRepository.save(course));
    }

    // ── Sections & Lessons ───────────────────────────────────

    @CacheEvict(value = COURSE_CACHE, key = "#courseId")
    public CourseResponse addSection(String courseId, AddSectionRequest request,
                                      UUID requestingUserId, String requestingRole) {
        Course course = loadAndAuthorize(courseId, requestingUserId, requestingRole);
        course.addSection(request.title(), request.description());
        return CourseResponse.from(courseRepository.save(course));
    }

    @CacheEvict(value = COURSE_CACHE, key = "#courseId")
    public CourseResponse addLesson(String courseId, String sectionId,
                                     AddLessonRequest request,
                                     UUID requestingUserId, String requestingRole) {
        Course course = loadAndAuthorize(courseId, requestingUserId, requestingRole);
        course.addLesson(
                sectionId, request.title(), request.description(),
                request.contentUrl(), request.lessonType(), request.durationMinutes()
        );
        return CourseResponse.from(courseRepository.save(course));
    }

    @CacheEvict(value = COURSE_CACHE, key = "#courseId")
    public CourseResponse removeLesson(String courseId, String sectionId, String lessonId,
                                        UUID requestingUserId, String requestingRole) {
        Course course = loadAndAuthorize(courseId, requestingUserId, requestingRole);
        course.removeLesson(sectionId, lessonId);
        return CourseResponse.from(courseRepository.save(course));
    }

    // ── Status Transitions ───────────────────────────────────

    @CacheEvict(value = COURSE_CACHE, key = "#courseId")
    public CourseResponse publishCourse(String courseId,
                                         UUID requestingUserId, String requestingRole) {
        Course course = loadAndAuthorize(courseId, requestingUserId, requestingRole);
        course.publish();
        Course saved = courseRepository.save(course);

        eventPublisher.publishCoursePublished(saved);
        log.info("Course published courseId={}", courseId);
        return CourseResponse.from(saved);
    }

    @CacheEvict(value = COURSE_CACHE, key = "#courseId")
    public CourseResponse archiveCourse(String courseId,
                                         UUID requestingUserId, String requestingRole) {
        Course course = loadAndAuthorize(courseId, requestingUserId, requestingRole);
        course.archive();
        Course saved = courseRepository.save(course);

        eventPublisher.publishCourseArchived(saved);
        log.info("Course archived courseId={}", courseId);
        return CourseResponse.from(saved);
    }

    // ── Internal (called by payment-service via event) ───────

    /**
     * Called after payment-service confirms payment.
     * Increments the enrollment counter on the course document.
     */
    @CacheEvict(value = COURSE_CACHE, key = "#courseId")
    public void incrementEnrollmentCount(String courseId) {
        courseRepository.findById(courseId).ifPresent(course -> {
            course.incrementEnrollments();
            courseRepository.save(course);
        });
    }

    // ── Helpers ──────────────────────────────────────────────

    private Course loadAndAuthorize(String courseId, UUID requestingUserId, String role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        boolean isAdmin = "ADMIN".equals(role);
        if (!isAdmin && !course.isOwnedBy(requestingUserId)) {
            throw new UnauthorizedCourseAccessException(
                "User " + requestingUserId + " does not own course " + courseId);
        }
        return course;
    }
}
