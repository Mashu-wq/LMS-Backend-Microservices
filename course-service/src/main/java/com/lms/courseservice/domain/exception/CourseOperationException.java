package com.lms.courseservice.domain.exception;

public class CourseOperationException extends RuntimeException {
    public CourseOperationException(String message) {
        super(message);
    }
}
