package com.lms.courseservice.infrastructure.persistence.mapper;

import com.lms.courseservice.domain.model.Enrollment;
import com.lms.courseservice.infrastructure.persistence.document.EnrollmentDocument;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentPersistenceMapper {

    public EnrollmentDocument toDocument(Enrollment enrollment) {
        return EnrollmentDocument.builder()
                .enrollmentId(enrollment.getEnrollmentId())
                .courseId(enrollment.getCourseId())
                .studentId(enrollment.getStudentId())
                .courseTitle(enrollment.getCourseTitle())
                .instructorId(enrollment.getInstructorId())
                .status(enrollment.getStatus())
                .enrolledAt(enrollment.getEnrolledAt())
                .completedAt(enrollment.getCompletedAt())
                .cancelledAt(enrollment.getCancelledAt())
                .build();
    }

    public Enrollment toDomain(EnrollmentDocument doc) {
        return Enrollment.reconstitute(
                doc.getEnrollmentId(), doc.getCourseId(), doc.getStudentId(),
                doc.getCourseTitle(), doc.getInstructorId(), doc.getStatus(),
                doc.getEnrolledAt(), doc.getCompletedAt(), doc.getCancelledAt()
        );
    }
}
