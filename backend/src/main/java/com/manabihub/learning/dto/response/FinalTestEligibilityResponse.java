package com.manabihub.learning.dto.response;

import java.util.UUID;

public record FinalTestEligibilityResponse(
        boolean configured,
        boolean eligible,
        String reason,
        UUID finalTestId,
        int totalLessons,
        int completedLessons,
        int attemptsUsed,
        int attemptsAllowed,
        boolean passed
) {
}
