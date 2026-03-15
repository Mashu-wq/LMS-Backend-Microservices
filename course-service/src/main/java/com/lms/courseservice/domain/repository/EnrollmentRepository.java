package com.lms.courseservice.domain.repository;

import com.lms.courseservice.domain.model.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository {

    Enrollment save(Enrollment enrollment);

    Optional<Enrollment> findById(String enrollmentId);

    Optional<Enrollment> findByStudentIdAndCourseId(UUID studentId, String courseId);

    boolean existsByStudentIdAndCourseId(UUID studentId, String courseId);

    Page<Enrollment> findByStudentId(UUID studentId, Pageable pageable);

    Page<Enrollment> findByCourseId(String courseId, Pageable pageable);

    List<String> findCourseIdsByStudentId(UUID studentId);
}
