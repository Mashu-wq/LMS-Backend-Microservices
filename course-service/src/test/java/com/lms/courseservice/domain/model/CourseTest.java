package com.lms.courseservice.domain.model;

import com.lms.courseservice.domain.exception.CourseOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Course Domain Model")
class CourseTest {

    private Course course;
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        course = Course.create("Java Microservices", "Full description", "Short desc",
                instructorId, "John Doe", "Programming", "English",
                "INTERMEDIATE", BigDecimal.valueOf(49.99), null);
    }

    @Nested
    @DisplayName("publish()")
    class Publish {

        @Test
        @DisplayName("should publish when course has at least one section with a lesson")
        void shouldPublishWithContent() {
            Section section = course.addSection("Getting Started", "Intro");
            course.addLesson(section.getSectionId(), "Hello World",
                    "First lesson", null, LessonType.VIDEO, 10);

            course.publish();

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(course.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw when no sections")
        void shouldThrowWithNoSections() {
            assertThatThrownBy(course::publish)
                    .isInstanceOf(CourseOperationException.class)
                    .hasMessageContaining("section");
        }

        @Test
        @DisplayName("should throw when trying to publish already published course")
        void shouldThrowWhenAlreadyPublished() {
            Section section = course.addSection("Section 1", null);
            course.addLesson(section.getSectionId(), "L1", null, null, LessonType.VIDEO, 5);
            course.publish();

            assertThatThrownBy(course::publish)
                    .isInstanceOf(CourseOperationException.class)
                    .hasMessageContaining("PUBLISHED");
        }
    }

    @Nested
    @DisplayName("section and lesson management")
    class ContentManagement {

        @Test
        @DisplayName("should add section and lesson correctly")
        void shouldAddContent() {
            Section section = course.addSection("Section 1", "Description");
            course.addLesson(section.getSectionId(), "Lesson 1", "Desc",
                    "https://video.url", LessonType.VIDEO, 30);

            assertThat(course.getSections()).hasSize(1);
            assertThat(course.getSections().get(0).getLessons()).hasSize(1);
            assertThat(course.totalLessonCount()).isEqualTo(1);
            assertThat(course.totalDurationMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("should throw when adding section to published course")
        void shouldThrowAddingSectionToPublished() {
            Section section = course.addSection("S1", null);
            course.addLesson(section.getSectionId(), "L1", null, null, LessonType.VIDEO, 1);
            course.publish();

            assertThatThrownBy(() -> course.addSection("New Section", null))
                    .isInstanceOf(CourseOperationException.class)
                    .hasMessageContaining("PUBLISHED");
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("should recognize the owning instructor")
        void shouldRecognizeOwner() {
            assertThat(course.isOwnedBy(instructorId)).isTrue();
        }

        @Test
        @DisplayName("should not recognize a different user as owner")
        void shouldNotRecognizeNonOwner() {
            assertThat(course.isOwnedBy(UUID.randomUUID())).isFalse();
        }
    }

    @Nested
    @DisplayName("status transitions")
    class StatusTransitions {

        @Test
        @DisplayName("DRAFT -> PUBLISHED -> ARCHIVED should be valid")
        void shouldFollowValidTransitions() {
            Section s = course.addSection("S", null);
            course.addLesson(s.getSectionId(), "L", null, null, LessonType.VIDEO, 5);
            course.publish();
            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            course.archive();
            assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
        }
    }
}
