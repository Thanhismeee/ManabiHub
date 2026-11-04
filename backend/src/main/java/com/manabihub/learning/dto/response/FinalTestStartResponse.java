package com.manabihub.learning.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FinalTestStartResponse(
        UUID attemptId,
        int timeLimitMinutes,
        int passingScore,
        int attemptsRemaining,
        Instant startedAt,
        Instant expiresAt,
        List<FinalTestQuestionView> questions
) {
    public record FinalTestQuestionView(
            UUID id,
            String content,
            List<FinalTestChoiceView> choices
    ) {
    }

    public record FinalTestChoiceView(
            UUID id,
            String content
    ) {
    }
}
