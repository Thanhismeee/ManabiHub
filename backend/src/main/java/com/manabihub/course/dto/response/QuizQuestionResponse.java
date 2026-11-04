package com.manabihub.course.dto.response;

import java.util.List;

public record QuizQuestionResponse(
        String question,
        List<String> options,
        String answer
) {
}
