package com.manabihub.challenge.job;

import com.manabihub.challenge.enums.ChallengeStatus;
import com.manabihub.challenge.repository.WeeklyLearningChallengeRepository;
import com.manabihub.challenge.service.ChallengeRewardSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.TemporalAdjusters;

@Component
@RequiredArgsConstructor
public class ChallengeRewardJobs {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final WeeklyLearningChallengeRepository challengeRepository;
    private final ChallengeRewardSettlementService settlementService;

    /**
     * Daily attendance is closed after the business day, never during an
     * unfinished day. Replaying the last eight days makes the job self-healing
     * after a short deployment outage; database and wallet idempotency keys make
     * every replay safe.
     */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void settleRecentAttendanceDays() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        for (int daysAgo = 1; daysAgo <= 8; daysAgo++) {
            settlementService.settleDailyAttendance(today.minusDays(daysAgo));
        }
    }

    /** Weekly rankings are immutable only after Sunday has ended. */
    @Scheduled(cron = "0 30 0 * * MON", zone = "Asia/Ho_Chi_Minh")
    public void settleCompletedWeeks() {
        LocalDate currentWeek = LocalDate.now(BUSINESS_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        challengeRepository.findByStatusAndWeekStartLessThanEqualAndSettledAtIsNull(
                        ChallengeStatus.PUBLISHED, currentWeek.minusWeeks(1))
                .forEach(challenge -> settlementService.settleWeeklyChallenge(challenge.getId()));
    }
}
