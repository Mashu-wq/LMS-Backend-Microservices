package com.lms.userservice.domain.exception;

import java.util.UUID;

public class UserProfileAlreadyExistsException extends RuntimeException {

    public UserProfileAlreadyExistsException(UUID userId) {
        super("User profile already exists for userId: " + userId);
    }
}
