package com.manabihub.course.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record QuizQuestionRequest(
        @Size(max = 500) String question,
        List<@Size(max = 500) String> options,
        @Size(max = 500) String answer
) {
}
