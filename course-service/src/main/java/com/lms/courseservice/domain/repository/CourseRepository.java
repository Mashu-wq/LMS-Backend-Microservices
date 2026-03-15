package com.lms.courseservice.domain.repository;

import com.lms.courseservice.domain.model.Course;
import com.lms.courseservice.domain.model.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    Course save(Course course);

    Optional<Course> findById(String courseId);

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    Page<Course> findByInstructorId(UUID instructorId, Pageable pageable);

    Page<Course> findByStatusAndCategory(CourseStatus status, String category, Pageable pageable);

    List<Course> findByIdIn(List<String> courseIds);

    void deleteById(String courseId);
}
