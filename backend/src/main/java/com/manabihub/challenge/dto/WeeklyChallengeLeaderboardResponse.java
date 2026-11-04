package com.manabihub.challenge.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WeeklyChallengeLeaderboardResponse(
        UUID challengeId,
        String challengeTitle,
        LocalDate weekStart,
        LocalDate weekEnd,
        boolean settled,
        Instant generatedAt,
        long totalParticipants,
        List<LeaderboardEntry> entries,
        LeaderboardEntry currentStudent
) {
    public record LeaderboardEntry(
            int rank,
            String displayName,
            String avatarUrl,
            long bestMillis,
            BigDecimal rewardAmount,
            boolean currentStudent
    ) {}
}
