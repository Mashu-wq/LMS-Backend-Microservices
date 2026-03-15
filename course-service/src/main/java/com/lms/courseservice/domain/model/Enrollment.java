package com.lms.courseservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Enrollment — tracks the student ↔ course relationship.
 *
 * <p>Stored as a SEPARATE MongoDB collection (not embedded in Course).
 * Rationale: a course can have thousands of enrollments — embedding them
 * would make the Course document unbounded in size, breaking MongoDB's
 * 16MB document limit and killing read performance.
 *
 * <p>Enrollment is created here by course-service when payment is confirmed.
 * Progress-service reads enrollment to know which courses a student has access to.
 */
public class Enrollment {

    public enum EnrollmentStatus { ACTIVE, COMPLETED, CANCELLED, REFUNDED }

    private final String enrollmentId;
    private final String courseId;
    private final UUID studentId;
    private final String courseTitle;   // Denormalized for list queries
    private final UUID instructorId;    // Denormalized for analytics
    private EnrollmentStatus status;
    private final Instant enrolledAt;
    private Instant completedAt;
    private Instant cancelledAt;

    private Enrollment(String enrollmentId, String courseId, UUID studentId,
                       String courseTitle, UUID instructorId) {
        this.enrollmentId = enrollmentId;
        this.courseId = courseId;
        this.studentId = studentId;
        this.courseTitle = courseTitle;
        this.instructorId = instructorId;
        this.status = EnrollmentStatus.ACTIVE;
        this.enrolledAt = Instant.now();
    }

    public static Enrollment create(String courseId, UUID studentId,
                                     String courseTitle, UUID instructorId) {
        return new Enrollment(UUID.randomUUID().toString(), courseId,
                studentId, courseTitle, instructorId);
    }

    public static Enrollment reconstitute(String enrollmentId, String courseId, UUID studentId,
                                           String courseTitle, UUID instructorId,
                                           EnrollmentStatus status, Instant enrolledAt,
                                           Instant completedAt, Instant cancelledAt) {
        Enrollment e = new Enrollment(enrollmentId, courseId, studentId, courseTitle, instructorId);
        e.status = status;
        e.completedAt = completedAt;
        e.cancelledAt = cancelledAt;
        return e;
    }

    public void complete() {
        if (this.status != EnrollmentStatus.ACTIVE) {
            throw new IllegalStateException("Cannot complete enrollment in status: " + status);
        }
        this.status = EnrollmentStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        if (this.status == EnrollmentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed enrollment");
        }
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public void refund() {
        this.status = EnrollmentStatus.REFUNDED;
        this.cancelledAt = Instant.now();
    }

    public boolean isActive() { return this.status == EnrollmentStatus.ACTIVE; }

    public String getEnrollmentId() { return enrollmentId; }
    public String getCourseId() { return courseId; }
    public UUID getStudentId() { return studentId; }
    public String getCourseTitle() { return courseTitle; }
    public UUID getInstructorId() { return instructorId; }
    public EnrollmentStatus getStatus() { return status; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
