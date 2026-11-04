package com.manabihub.learning.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuizSubmissionRequest(
        @NotNull List<@NotBlank String> answers
) {
}
