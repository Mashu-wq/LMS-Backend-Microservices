package com.lms.courseservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddSectionRequest(
        @NotBlank(message = "Section title is required")
        @Size(max = 200)
        String title,

        @Size(max = 1000)
        String description
) {}
