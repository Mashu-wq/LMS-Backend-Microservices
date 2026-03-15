package com.lms.userservice.api.controller;

import com.lms.userservice.application.dto.request.UpdateProfileRequest;
import com.lms.userservice.application.dto.response.PageResponse;
import com.lms.userservice.application.dto.response.UserProfileResponse;
import com.lms.userservice.application.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User Profile REST Controller.
 *
 * <p>Ownership enforcement: users can only update their OWN profile.
 * Admins can read and manage any profile.
 * The JWT "sub" claim contains the authenticated user's UUID.
 *
 * <p>This controller's GET /users/v1/{userId} endpoint is called by:
 * - course-service: to resolve instructor name for course display
 * - progress-service: to validate student exists before tracking
 * - The Feign client in other services calls this exact endpoint
 */
@Slf4j
@RestController
@RequestMapping("/users/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    // ── GET /users/v1/{userId} ───────────────────────────────
    // Called by Feign clients in other services

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        // Users can only view their own profile unless they're an admin
        UUID requestingUserId = UUID.fromString(jwt.getSubject());
        String role = jwt.getClaimAsString("role");

        if (!requestingUserId.equals(userId) && !"ADMIN".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }

    // ── GET /users/v1/me ─────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }

    // ── PUT /users/v1/me ─────────────────────────────────────

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
    }

    // ── PATCH /users/v1/{userId} — Admin can update any profile ─

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
    }

    // ── Admin endpoints ──────────────────────────────────────

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserProfileResponse>> listAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), sort);
        return ResponseEntity.ok(userProfileService.listProfiles(pageable));
    }

    @GetMapping("/admin/users/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserProfileResponse>> listUsersByRole(
            @PathVariable String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(userProfileService.listProfilesByRole(role.toUpperCase(), pageable));
    }

    @PostMapping("/admin/users/{userId}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> suspendUser(@PathVariable UUID userId) {
        userProfileService.suspendUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/users/{userId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reactivateUser(@PathVariable UUID userId) {
        userProfileService.reactivateUser(userId);
        return ResponseEntity.noContent().build();
    }

    // ── Internal endpoint for Feign clients ─────────────────
    // No auth required — internal network only (gateway doesn't expose /internal/**)

    @GetMapping("/internal/{userId}")
    public ResponseEntity<UserProfileResponse> getProfileInternal(@PathVariable UUID userId) {
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }
}
