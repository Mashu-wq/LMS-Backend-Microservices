package com.lms.courseservice.infrastructure.persistence.document;

import com.lms.courseservice.domain.model.Enrollment.EnrollmentStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "enrollments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "idx_student_course", def = "{'studentId': 1, 'courseId': 1}", unique = true),
    @CompoundIndex(name = "idx_course_status", def = "{'courseId': 1, 'status': 1}")
})
public class EnrollmentDocument {

    @Id
    private String enrollmentId;

    @Indexed
    private String courseId;

    @Indexed
    private UUID studentId;

    private String courseTitle;
    private UUID instructorId;
    private EnrollmentStatus status;
    private Instant enrolledAt;
    private Instant completedAt;
    private Instant cancelledAt;
}
