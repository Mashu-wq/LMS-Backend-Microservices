package com.lms.userservice.domain.exception;

import java.util.UUID;

public class UserProfileNotFoundException extends RuntimeException {

    public UserProfileNotFoundException(UUID userId) {
        super("User profile not found for userId: " + userId);
    }

    public UserProfileNotFoundException(String email) {
        super("User profile not found for email: " + email);
    }
}
