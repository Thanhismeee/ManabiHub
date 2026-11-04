package com.manabihub.learning.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FinalTestSubmissionResponse(
        UUID attemptId,
        BigDecimal score,
        boolean passed,
        boolean certificateBlocked,
        int correctCount,
        int totalQuestions,
        List<FinalTestQuestionFeedback> feedback
) {
    public record FinalTestQuestionFeedback(
            UUID questionId,
            boolean correct,
            String explanation,
            List<UUID> correctChoiceIds
    ) {
    }
}
