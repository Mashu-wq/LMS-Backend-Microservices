package com.lms.authservice.api.exception;

import com.lms.authservice.infrastructure.security.exception.AccountLockedException;
import com.lms.authservice.infrastructure.security.exception.InvalidCredentialsException;
import com.lms.authservice.infrastructure.security.exception.InvalidTokenException;
import com.lms.authservice.infrastructure.security.exception.UserAlreadyExistsException;
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

/**
 * Global exception handler — translates domain exceptions to RFC 7807 Problem Details.
 *
 * <p>Why RFC 7807? It's the industry standard for structured error responses.
 * Clients (frontend, other services) can reliably parse errors by type URI.
 * Spring 6+ / Spring Boot 3+ have native ProblemDetail support.
 *
 * <p>Security note: error messages are deliberately vague for auth failures
 * (no indication of whether email or password was wrong — prevents user enumeration).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE_URI = "https://lms-platform.com/errors";

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + "/user-already-exists"));
        problem.setTitle("User Already Exists");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
        // Log the real reason internally but return a generic message externally
        log.debug("Invalid credentials: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid email or password");
        problem.setType(URI.create(PROBLEM_BASE_URI + "/invalid-credentials"));
        problem.setTitle("Authentication Failed");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ProblemDetail> handleAccountLocked(AccountLockedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + "/account-locked"));
        problem.setTitle("Account Temporarily Locked");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        problem.setType(URI.create(PROBLEM_BASE_URI + "/invalid-token"));
        problem.setTitle("Token Validation Failed");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a  // keep first if duplicate fields
                ));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Request validation failed");
        problem.setType(URI.create(PROBLEM_BASE_URI + "/validation-error"));
        problem.setTitle("Validation Error");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(URI.create(PROBLEM_BASE_URI + "/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
