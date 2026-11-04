package com.manabihub.learning.dto.internal;

import java.util.List;

public record InternalQuizQuestionDto(
        String question,
        List<String> options,
        String answer
) {
}
