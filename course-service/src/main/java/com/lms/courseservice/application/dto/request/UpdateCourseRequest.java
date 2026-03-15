package com.lms.courseservice.application.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record UpdateCourseRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 5000)
        String description,

        @Size(max = 300)
        String shortDescription,

        String category,
        String language,

        @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED")
        String level,

        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal price,

        String thumbnailUrl,
        List<String> tags
) {}
