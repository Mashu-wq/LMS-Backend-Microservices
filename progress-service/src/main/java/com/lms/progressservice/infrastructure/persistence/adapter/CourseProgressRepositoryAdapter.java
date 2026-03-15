package com.lms.progressservice.infrastructure.persistence.adapter;

import com.lms.progressservice.domain.model.CourseProgress;
import com.lms.progressservice.domain.repository.CourseProgressRepository;
import com.lms.progressservice.infrastructure.persistence.entity.CourseProgressEntity;
import com.lms.progressservice.infrastructure.persistence.entity.LessonCompletionEntity;
import com.lms.progressservice.infrastructure.persistence.entity.LessonCompletionEntity.LessonCompletionId;
import com.lms.progressservice.infrastructure.persistence.repository.JpaCourseProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CourseProgressRepositoryAdapter implements CourseProgressRepository {

    private final JpaCourseProgressRepository jpaRepository;

    @Override
    public CourseProgress save(CourseProgress progress) {
        CourseProgressEntity entity = toEntity(progress);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<CourseProgress> findByStudentIdAndCourseId(UUID studentId, String courseId) {
        return jpaRepository.findByStudentIdAndCourseId(studentId, courseId).map(this::toDomain);
    }

    @Override
    public Optional<CourseProgress> findById(UUID progressId) {
        return jpaRepository.findById(progressId).map(this::toDomain);
    }

    @Override
    public Page<CourseProgress> findByStudentId(UUID studentId, Pageable pageable) {
        return jpaRepository.findByStudentId(studentId, pageable).map(this::toDomain);
    }

    @Override
    public Page<CourseProgress> findByCourseId(String courseId, Pageable pageable) {
        return jpaRepository.findByCourseId(courseId, pageable).map(this::toDomain);
    }

    @Override
    public boolean existsByStudentIdAndCourseId(UUID studentId, String courseId) {
        return jpaRepository.existsByStudentIdAndCourseId(studentId, courseId);
    }

    // ── Mapping ─────────────────────────────────────────────────────────────

    private CourseProgressEntity toEntity(CourseProgress progress) {
        // Build the set of LessonCompletionEntity from domain's completedLessonIds
        Set<LessonCompletionEntity> completions = progress.getCompletedLessonIds().stream()
                .map(lessonId -> LessonCompletionEntity.builder()
                        .id(new LessonCompletionId(progress.getProgressId(), lessonId))
                        .build())
                .collect(Collectors.toSet());

        return CourseProgressEntity.builder()
                .progressId(progress.getProgressId())
                .studentId(progress.getStudentId())
                .courseId(progress.getCourseId())
                .courseTitle(progress.getCourseTitle())
                .totalLessons(progress.getTotalLessons())
                .status(progress.getStatus())
                .enrolledAt(progress.getEnrolledAt())
                .completedAt(progress.getCompletedAt())
                .lastActivityAt(progress.getLastActivityAt())
                .lessonCompletions(completions)
                .build();
    }

    private CourseProgress toDomain(CourseProgressEntity entity) {
        Set<String> completedLessonIds = entity.getLessonCompletions().stream()
                .map(lc -> lc.getId().getLessonId())
                .collect(Collectors.toSet());

        return CourseProgress.reconstitute(
                entity.getProgressId(),
                entity.getStudentId(),
                entity.getCourseId(),
                entity.getCourseTitle(),
                entity.getTotalLessons(),
                completedLessonIds,
                entity.getStatus(),
                entity.getEnrolledAt(),
                entity.getCompletedAt(),
                entity.getLastActivityAt()
        );
    }
}
