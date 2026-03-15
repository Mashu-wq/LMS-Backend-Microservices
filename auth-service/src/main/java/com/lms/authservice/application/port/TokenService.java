package com.lms.authservice.application.port;

import com.lms.authservice.domain.model.User;

import java.util.Map;

/**
 * Port for token operations — the application layer depends on this
 * abstraction, not the JWT implementation directly.
 * This makes the use cases testable without a real JWT library.
 */
public interface TokenService {

    String generateAccessToken(User user);

    String generateRefreshToken();

    /**
     * Validates an access token and returns its claims.
     * Throws an exception if the token is invalid or expired.
     */
    Map<String, Object> validateAccessToken(String token);

    long accessTokenExpiresInSeconds();
}
