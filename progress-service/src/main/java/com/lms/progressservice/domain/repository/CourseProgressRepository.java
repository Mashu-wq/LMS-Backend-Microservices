package com.lms.progressservice.domain.repository;

import com.lms.progressservice.domain.model.CourseProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CourseProgressRepository {

    CourseProgress save(CourseProgress progress);

    Optional<CourseProgress> findByStudentIdAndCourseId(UUID studentId, String courseId);

    Optional<CourseProgress> findById(UUID progressId);

    Page<CourseProgress> findByStudentId(UUID studentId, Pageable pageable);

    Page<CourseProgress> findByCourseId(String courseId, Pageable pageable);

    boolean existsByStudentIdAndCourseId(UUID studentId, String courseId);
}
