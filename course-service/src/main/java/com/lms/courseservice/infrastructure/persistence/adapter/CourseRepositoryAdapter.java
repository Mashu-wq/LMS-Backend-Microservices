package com.lms.courseservice.infrastructure.persistence.adapter;

import com.lms.courseservice.domain.model.Course;
import com.lms.courseservice.domain.model.CourseStatus;
import com.lms.courseservice.domain.repository.CourseRepository;
import com.lms.courseservice.infrastructure.persistence.mapper.CoursePersistenceMapper;
import com.lms.courseservice.infrastructure.persistence.repository.MongoCourseCrudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseRepositoryAdapter implements CourseRepository {

    private final MongoCourseCrudRepository mongoRepo;
    private final CoursePersistenceMapper mapper;

    @Override
    public Course save(Course course) {
        return mapper.toDomain(mongoRepo.save(mapper.toDocument(course)));
    }

    @Override
    public Optional<Course> findById(String courseId) {
        return mongoRepo.findById(courseId).map(mapper::toDomain);
    }

    @Override
    public Page<Course> findByStatus(CourseStatus status, Pageable pageable) {
        return mongoRepo.findByStatus(status, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Course> findByInstructorId(UUID instructorId, Pageable pageable) {
        return mongoRepo.findByInstructorId(instructorId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Course> findByStatusAndCategory(CourseStatus status, String category, Pageable pageable) {
        return mongoRepo.findByStatusAndCategory(status, category, pageable).map(mapper::toDomain);
    }

    @Override
    public List<Course> findByIdIn(List<String> courseIds) {
        return mongoRepo.findAllById(courseIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String courseId) {
        mongoRepo.deleteById(courseId);
    }
}
