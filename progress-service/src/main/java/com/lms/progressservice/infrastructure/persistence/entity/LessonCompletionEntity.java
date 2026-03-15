package com.lms.progressservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_completions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonCompletionEntity {

    @EmbeddedId
    private LessonCompletionId id;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        if (completedAt == null) completedAt = Instant.now();
    }

    // ── Composite PK ────────────────────────────────────────────────────────

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class LessonCompletionId implements Serializable {

        @Column(name = "progress_id")
        private UUID progressId;

        @Column(name = "lesson_id", length = 50)
        private String lessonId;
    }
}
