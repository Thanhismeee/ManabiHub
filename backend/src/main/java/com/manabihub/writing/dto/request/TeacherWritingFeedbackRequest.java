package com.manabihub.writing.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TeacherWritingFeedbackRequest(
        @NotNull(message = "Score is required")
        @DecimalMin(value = "0.0", message = "Score must be at least 0")
        @DecimalMax(value = "10.0", message = "Score must not exceed 10")
        BigDecimal score,

        @NotBlank(message = "Feedback comment is required")
        @Size(max = 5000, message = "Feedback comment must not exceed 5000 characters")
        String comment
) {
}
