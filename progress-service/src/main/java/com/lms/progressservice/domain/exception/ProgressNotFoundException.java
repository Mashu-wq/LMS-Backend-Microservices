package com.lms.progressservice.domain.exception;

import java.util.UUID;

public class ProgressNotFoundException extends RuntimeException {

    public ProgressNotFoundException(UUID studentId, String courseId) {
        super("No progress record found for student " + studentId + " in course " + courseId);
    }
}
