package com.lms.courseservice.domain.exception;

public class UnauthorizedCourseAccessException extends RuntimeException {
    public UnauthorizedCourseAccessException(String message) {
        super(message);
    }
}
