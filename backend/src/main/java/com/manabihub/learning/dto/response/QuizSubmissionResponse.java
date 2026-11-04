package com.manabihub.learning.dto.response;

import com.manabihub.learning.enums.LessonProgressStatus;

import java.math.BigDecimal;
import java.util.List;

public record QuizSubmissionResponse(
        BigDecimal score,
        boolean passed,
        int correctCount,
        int totalQuestions,
        LessonProgressStatus progressStatus,
        List<QuizQuestionFeedback> feedback
) {
    public record QuizQuestionFeedback(
            int questionIndex,
            boolean correct,
            String correctAnswer
    ) {
    }
}
