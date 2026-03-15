package com.lms.courseservice.api.exception;

import com.lms.courseservice.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_URI = "https://lms-platform.com/errors";

    @ExceptionHandler(CourseNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(CourseNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "course-not-found");
    }

    @ExceptionHandler(CourseOperationException.class)
    public ResponseEntity<ProblemDetail> handleCourseOperation(CourseOperationException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "course-operation-error");
    }

    @ExceptionHandler(UnauthorizedCourseAccessException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedCourseAccessException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage(), "unauthorized-access");
    }

    @ExceptionHandler(EnrollmentException.class)
    public ResponseEntity<ProblemDetail> handleEnrollment(EnrollmentException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "enrollment-error");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid",
                        (a, b) -> a));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed");
        pd.setType(URI.create(BASE_URI + "/validation-error"));
        pd.setTitle("Validation Error");
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "internal-error");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, String type) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(BASE_URI + "/" + type));
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).body(pd);
    }
}
