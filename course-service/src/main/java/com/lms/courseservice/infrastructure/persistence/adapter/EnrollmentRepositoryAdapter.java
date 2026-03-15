package com.lms.courseservice.infrastructure.persistence.adapter;

import com.lms.courseservice.domain.model.Enrollment;
import com.lms.courseservice.domain.repository.EnrollmentRepository;
import com.lms.courseservice.infrastructure.persistence.mapper.EnrollmentPersistenceMapper;
import com.lms.courseservice.infrastructure.persistence.repository.MongoEnrollmentCrudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EnrollmentRepositoryAdapter implements EnrollmentRepository {

    private final MongoEnrollmentCrudRepository mongoRepo;
    private final EnrollmentPersistenceMapper mapper;

    @Override
    public Enrollment save(Enrollment enrollment) {
        return mapper.toDomain(mongoRepo.save(mapper.toDocument(enrollment)));
    }

    @Override
    public Optional<Enrollment> findById(String enrollmentId) {
        return mongoRepo.findById(enrollmentId).map(mapper::toDomain);
    }

    @Override
    public Optional<Enrollment> findByStudentIdAndCourseId(UUID studentId, String courseId) {
        return mongoRepo.findByStudentIdAndCourseId(studentId, courseId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByStudentIdAndCourseId(UUID studentId, String courseId) {
        return mongoRepo.existsByStudentIdAndCourseId(studentId, courseId);
    }

    @Override
    public Page<Enrollment> findByStudentId(UUID studentId, Pageable pageable) {
        return mongoRepo.findByStudentId(studentId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Enrollment> findByCourseId(String courseId, Pageable pageable) {
        return mongoRepo.findByCourseId(courseId, pageable).map(mapper::toDomain);
    }

    @Override
    public List<String> findCourseIdsByStudentId(UUID studentId) {
        return mongoRepo.findCourseIdsByStudentId(studentId).stream()
                .map(doc -> doc.getCourseId())
                .toList();
    }
}
