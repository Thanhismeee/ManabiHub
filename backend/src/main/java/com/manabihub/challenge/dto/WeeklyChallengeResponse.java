package com.manabihub.challenge.dto;

import com.manabihub.challenge.enums.ChallengeStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record WeeklyChallengeResponse(
        UUID id,
        LocalDate weekStart,
        LocalDate weekEnd,
        String title,
        String description,
        String jlptLevel,
        ChallengeStatus status,
        int dailyRankedLimit,
        int wrongPenaltySeconds,
        BigDecimal dailyAttendanceReward,
        BigDecimal firstPrize,
        BigDecimal secondPrize,
        BigDecimal thirdPrize,
        List<ChallengePairResponse> pairs,
        Instant publishedAt,
        Instant settledAt,
        Long personalBestMillis,
        long rankedAttemptsToday
) {
    public record ChallengePairResponse(UUID id, String prompt, String answer, int orderIndex) {}
}
