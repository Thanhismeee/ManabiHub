package com.manabihub.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseReviewRequest {

    @NotBlank(message = "Action is required")
    private String action; // "APPROVE", "REJECT", "REQUEST_CORRECTION"

    private String reason; // Required for REJECT and REQUEST_CORRECTION
}
