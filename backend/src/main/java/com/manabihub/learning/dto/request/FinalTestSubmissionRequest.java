package com.manabihub.learning.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record FinalTestSubmissionRequest(
        @NotNull UUID attemptId,
        @NotEmpty List<@Valid FinalTestAnswer> answers
) {
    public record FinalTestAnswer(
            @NotNull UUID questionId,
            @NotEmpty List<@NotNull UUID> selectedChoiceIds
    ) {
    }
}
