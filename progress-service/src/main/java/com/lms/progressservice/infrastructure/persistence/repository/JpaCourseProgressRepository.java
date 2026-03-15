package com.lms.progressservice.infrastructure.persistence.repository;

import com.lms.progressservice.infrastructure.persistence.entity.CourseProgressEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaCourseProgressRepository extends JpaRepository<CourseProgressEntity, UUID> {

    Optional<CourseProgressEntity> findByStudentIdAndCourseId(UUID studentId, String courseId);

    Page<CourseProgressEntity> findByStudentId(UUID studentId, Pageable pageable);

    Page<CourseProgressEntity> findByCourseId(String courseId, Pageable pageable);

    boolean existsByStudentIdAndCourseId(UUID studentId, String courseId);
}
