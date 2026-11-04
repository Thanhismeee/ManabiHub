package com.manabihub.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertCourseReviewRequest(
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        int rating,

        @NotBlank(message = "Review text is required")
        @Size(min = 10, max = 2000, message = "Review text must contain between 10 and 2000 characters")
        String reviewText
) {
}
