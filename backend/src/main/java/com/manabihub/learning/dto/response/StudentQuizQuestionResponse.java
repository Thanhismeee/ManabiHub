package com.manabihub.learning.dto.response;

import java.util.List;

public record StudentQuizQuestionResponse(
        String question,
        List<String> options
) {
}
