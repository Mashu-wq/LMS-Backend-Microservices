package com.lms.authservice.application.dto.response;

/**
 * Internal result of a login or token-refresh operation.
 *
 * <p>The raw refresh token must travel from the service to the controller
 * so the controller can set it as an HttpOnly cookie on the HTTP response.
 * It is never serialised to JSON — only {@link AuthResponse} goes to the client.
 */
public record LoginResult(
        AuthResponse authResponse,
        String rawRefreshToken
) {}
