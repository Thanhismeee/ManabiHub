package com.manabihub.challenge.dto;

import java.time.Instant;
import java.util.*;

public record ChallengeAttemptResponse(
        UUID attemptId,
        boolean ranked,
        int remainingRankedAttempts,
        List<ChallengeCardResponse> cards,
        int matchedPairs,
        int totalPairs,
        long penaltyMillis,
        Long totalMillis,
        Instant expiresAt,
        boolean completed
) {
    public record ChallengeCardResponse(UUID id, String value, int position, boolean matched) {}
}
