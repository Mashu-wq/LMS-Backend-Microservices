package com.lms.courseservice.infrastructure.persistence.repository;

import com.lms.courseservice.domain.model.CourseStatus;
import com.lms.courseservice.infrastructure.persistence.document.CourseDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface MongoCourseCrudRepository extends MongoRepository<CourseDocument, String> {

    Page<CourseDocument> findByStatus(CourseStatus status, Pageable pageable);

    Page<CourseDocument> findByInstructorId(UUID instructorId, Pageable pageable);

    Page<CourseDocument> findByStatusAndCategory(CourseStatus status, String category, Pageable pageable);
}
