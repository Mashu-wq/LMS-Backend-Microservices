package com.lms.authservice.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Returned after successful login or token refresh.
 * The refresh token is set as an HttpOnly cookie by the controller,
 * not included in the body — this prevents XSS from reading it.
 * Access token IS in the body so JS clients can use it in Authorization headers.
 */
public record AuthResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn,

        @JsonProperty("user_id")
        UUID userId,

        String email,
        String role
) {
    public static AuthResponse of(String accessToken, long expiresIn,
                                   UUID userId, String email, String role) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, userId, email, role);
    }
}
