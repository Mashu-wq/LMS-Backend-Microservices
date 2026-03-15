package com.lms.authservice.application.dto.response;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String email,
        String role,
        String message
) {
    public static RegisterResponse of(UUID userId, String email, String role) {
        return new RegisterResponse(userId, email, role,
                "Registration successful. Please verify your email.");
    }
}
