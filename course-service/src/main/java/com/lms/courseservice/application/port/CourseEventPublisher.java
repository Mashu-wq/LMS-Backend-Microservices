package com.lms.courseservice.application.port;

import com.lms.courseservice.domain.model.Course;
import com.lms.courseservice.domain.model.Enrollment;

public interface CourseEventPublisher {

    void publishCoursePublished(Course course);

    void publishStudentEnrolled(Enrollment enrollment);

    void publishCourseArchived(Course course);
}
