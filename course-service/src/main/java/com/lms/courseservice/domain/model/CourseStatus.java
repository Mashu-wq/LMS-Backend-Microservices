package com.lms.courseservice.domain.model;

/**
 * Course lifecycle state machine.
 *
 * <p>Valid transitions:
 *   DRAFT      → PUBLISHED  (by instructor when course has ≥1 published section)
 *   PUBLISHED  → ARCHIVED   (by instructor or admin)
 *   ARCHIVED   → PUBLISHED  (re-publish, by admin only)
 *   DRAFT      → ARCHIVED   (discard draft, by instructor or admin)
 *
 * <p>Transition logic lives in the Course aggregate, not the service layer.
 */
public enum CourseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean canTransitionTo(CourseStatus target) {
        return switch (this) {
            case DRAFT     -> target == PUBLISHED || target == ARCHIVED;
            case PUBLISHED -> target == ARCHIVED;
            case ARCHIVED  -> target == PUBLISHED;
        };
    }
}
