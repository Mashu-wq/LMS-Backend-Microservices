-- Progress Service Schema
-- course_progress: one row per (student, course) pair — the aggregate root.
-- lesson_completions: one row per lesson a student has completed in a course.

CREATE TABLE course_progress (
    progress_id      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID          NOT NULL,
    course_id        VARCHAR(50)   NOT NULL,
    course_title     VARCHAR(500)  NOT NULL,
    total_lessons    INT           NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS'
                                   CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    enrolled_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMPTZ,
    last_activity_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_progress_student_course UNIQUE (student_id, course_id)
);

-- Per-lesson completion log (each row is immutable once inserted)
CREATE TABLE lesson_completions (
    progress_id  UUID         NOT NULL REFERENCES course_progress(progress_id) ON DELETE CASCADE,
    lesson_id    VARCHAR(50)  NOT NULL,
    completed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    PRIMARY KEY (progress_id, lesson_id)
);

-- Indexes for common access patterns
CREATE INDEX idx_course_progress_student_id ON course_progress (student_id);
CREATE INDEX idx_course_progress_course_id  ON course_progress (course_id);
CREATE INDEX idx_course_progress_status     ON course_progress (status);
CREATE INDEX idx_lesson_completions_progress ON lesson_completions (progress_id);
