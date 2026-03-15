package com.lms.userservice.application.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @Size(min = 2, max = 50, message = "First name must be 2–50 characters")
        String firstName,

        @Size(min = 2, max = 50, message = "Last name must be 2–50 characters")
        String lastName,

        @Size(max = 500, message = "Bio must not exceed 500 characters")
        String bio,

        @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
        String avatarUrl
) {}
