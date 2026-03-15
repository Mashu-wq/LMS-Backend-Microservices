package com.lms.progressservice.infrastructure.persistence.entity;

import com.lms.progressservice.domain.model.ProgressStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    name = "course_progress",
    indexes = {
        @Index(name = "idx_course_progress_student_id", columnList = "student_id"),
        @Index(name = "idx_course_progress_course_id",  columnList = "course_id"),
        @Index(name = "idx_course_progress_status",     columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseProgressEntity {

    @Id
    @Column(name = "progress_id", updatable = false, nullable = false)
    private UUID progressId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id", nullable = false, length = 50)
    private String courseId;

    @Column(name = "course_title", nullable = false, length = 500)
    private String courseTitle;

    @Column(name = "total_lessons", nullable = false)
    private int totalLessons;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProgressStatus status;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "progress_id")
    @Builder.Default
    private Set<LessonCompletionEntity> lessonCompletions = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (enrolledAt == null)      enrolledAt = Instant.now();
        if (lastActivityAt == null)  lastActivityAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastActivityAt = Instant.now();
    }
}
