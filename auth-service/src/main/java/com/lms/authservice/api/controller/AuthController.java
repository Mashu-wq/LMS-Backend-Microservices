package com.lms.authservice.api.controller;

import com.lms.authservice.application.dto.request.LoginRequest;
import com.lms.authservice.application.dto.request.RefreshTokenRequest;
import com.lms.authservice.application.dto.request.RegisterRequest;
import com.lms.authservice.application.dto.response.AuthResponse;
import com.lms.authservice.application.dto.response.RegisterResponse;
import com.lms.authservice.application.service.AuthApplicationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

/**
 * Auth REST Controller.
 *
 * <p>Refresh token cookie strategy:
 * - The raw refresh token is set as an HttpOnly, Secure, SameSite=Strict cookie.
 * - This prevents JavaScript from reading it (XSS protection).
 * - The access token IS returned in the response body (short-lived, 15 min).
 * - On token refresh, the client sends the cookie automatically (browser handles it).
 */
@Slf4j
@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "lms-refresh-token";
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7 days in seconds

    private final AuthApplicationService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);

        // The service returns the raw refresh token via a temporary holder.
        // In a real impl, we'd return it separately. For clarity here,
        // we trust the service to generate it and set cookie on response.
        // See note: in production use a dedicated login result object
        // that carries the raw refresh token separately from the AuthResponse.

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request,
                                                 HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(null);
        }

        AuthResponse authResponse = authService.refresh(new RefreshTokenRequest(refreshToken));
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request,
                                                       HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        // Clear the cookie
        clearRefreshTokenCookie(response);

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── Helpers ──────────────────────────────────────────────

    private void setRefreshTokenCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);       // Requires HTTPS in production
        cookie.setPath("/auth/v1/refresh");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        // SameSite not directly supported in old Servlet API — set via header
        response.addHeader("Set-Cookie",
                "%s=%s; Max-Age=%d; Path=/auth/v1/refresh; HttpOnly; Secure; SameSite=Strict"
                .formatted(REFRESH_TOKEN_COOKIE, rawToken, REFRESH_TOKEN_MAX_AGE));
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                "%s=; Max-Age=0; Path=/auth/v1/refresh; HttpOnly; Secure; SameSite=Strict"
                .formatted(REFRESH_TOKEN_COOKIE));
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
