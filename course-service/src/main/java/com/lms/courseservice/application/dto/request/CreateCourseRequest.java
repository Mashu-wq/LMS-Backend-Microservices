package com.lms.courseservice.application.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateCourseRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @NotBlank(message = "Short description is required")
        @Size(max = 300, message = "Short description must not exceed 300 characters")
        String shortDescription,

        @NotBlank(message = "Category is required")
        String category,

        @NotBlank(message = "Language is required")
        String language,

        @NotBlank(message = "Level is required")
        @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "Level must be BEGINNER, INTERMEDIATE, or ADVANCED")
        String level,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must be 0 or greater")
        @Digits(integer = 8, fraction = 2, message = "Invalid price format")
        BigDecimal price,

        String thumbnailUrl,

        List<String> tags
) {}
