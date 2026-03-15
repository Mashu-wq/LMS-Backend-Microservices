package com.lms.courseservice.infrastructure.persistence.repository;

import com.lms.courseservice.infrastructure.persistence.document.EnrollmentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MongoEnrollmentCrudRepository extends MongoRepository<EnrollmentDocument, String> {

    Optional<EnrollmentDocument> findByStudentIdAndCourseId(UUID studentId, String courseId);

    boolean existsByStudentIdAndCourseId(UUID studentId, String courseId);

    Page<EnrollmentDocument> findByStudentId(UUID studentId, Pageable pageable);

    Page<EnrollmentDocument> findByCourseId(String courseId, Pageable pageable);

    @Query(value = "{ 'studentId': ?0 }", fields = "{ 'courseId': 1, '_id': 0 }")
    List<EnrollmentDocument> findCourseIdsByStudentId(UUID studentId);
}
