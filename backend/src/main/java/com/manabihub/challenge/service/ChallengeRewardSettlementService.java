package com.manabihub.challenge.service;

import com.manabihub.challenge.entity.*;
import com.manabihub.challenge.enums.ChallengeStatus;
import com.manabihub.challenge.repository.*;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.service.StudentWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChallengeRewardSettlementService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final WeeklyLearningChallengeRepository challengeRepository;
    private final WeeklyLearningChallengeAttemptRepository attemptRepository;
    private final WeeklyLearningChallengeRewardRepository rewardRepository;
    private final DailyLearningAttendanceRewardRepository attendanceRewardRepository;
    private final LessonBlockProgressRepository progressRepository;
    private final StudentWalletService walletService;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void settleWeeklyChallenge(UUID challengeId) {
        WeeklyLearningChallenge challenge = challengeRepository.findByIdForUpdate(challengeId).orElse(null);
        if (challenge == null) return;
        settleWeeklyChallenge(challenge);
    }

    /**
     * Re-runs settlement for a named challenge after an operational failure.
     * The endpoint that calls this method is restricted to SYSTEM_ADMIN; every
     * invocation is audited, including idempotent retries that do not settle.
     */
    @Transactional
    public void retryWeeklySettlement(UUID adminId, UUID challengeId) {
        WeeklyLearningChallenge challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new BusinessException(
                        "WEEKLY_CHALLENGE_NOT_FOUND", "Không tìm thấy thử thách", org.springframework.http.HttpStatus.NOT_FOUND));
        boolean settled = settleWeeklyChallenge(challenge);
        auditLogRepository.save(AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(adminId)
                .actorRoleCode("SYSTEM_ADMIN")
                .action("WEEKLY_CHALLENGE_SETTLEMENT_RETRIED")
                .targetType("WEEKLY_CHALLENGE")
                .targetId(challengeId)
                .metadata(Map.of("outcome", settled ? "SETTLED" : "NO_ACTION"))
                .build());
    }

    private boolean settleWeeklyChallenge(WeeklyLearningChallenge challenge) {
        if (challenge.getSettledAt() != null || challenge.getStatus() != ChallengeStatus.PUBLISHED) return false;
        LocalDate currentWeek = LocalDate.now(BUSINESS_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!challenge.getWeekStart().isBefore(currentWeek)) return false;

        List<WeeklyLearningChallengeAttemptRepository.BestScore> scores =
                attemptRepository.findRankedBestScores(challenge.getId());
        List<BigDecimal> prizes = List.of(challenge.getFirstPrize(), challenge.getSecondPrize(), challenge.getThirdPrize());
        for (int i = 0; i < Math.min(3, scores.size()); i++) {
            BigDecimal amount = prizes.get(i);
            UUID studentId = scores.get(i).getStudentId();
            if (amount.signum() <= 0 || rewardRepository.existsByChallengeIdAndStudentId(challenge.getId(), studentId)) continue;
            int rank = i + 1;
            WalletTransaction transaction = walletService.creditPromotionalReward(studentId, amount,
                    WalletTransactionType.GAME_REWARD, "WEEKLY_CHALLENGE", challenge.getId(),
                    "weekly-game:" + challenge.getId() + ":" + studentId,
                    "Thưởng hạng " + rank + " thử thách tuần " + challenge.getWeekStart());
            rewardRepository.save(WeeklyLearningChallengeReward.builder().id(UUID.randomUUID())
                    .challengeId(challenge.getId()).studentId(studentId).rankPosition(rank).amount(amount)
                    .walletTransactionId(transaction.getId()).awardedAt(Instant.now()).build());
        }
        challenge.setSettledAt(Instant.now());
        challenge.setStatus(ChallengeStatus.ARCHIVED);
        challengeRepository.save(challenge);
        return true;
    }

    @Transactional
    public void settleDailyAttendance(LocalDate rewardDate) {
        LocalDate weekStart = rewardDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyLearningChallenge challenge = challengeRepository
                .findByWeekStartForUpdate(weekStart).orElse(null);
        // ARCHIVED is intentionally accepted: on Monday the weekly ranking can be
        // settled before a delayed/retried Sunday attendance job. DRAFT must never
        // mint a reward because students could not have joined that challenge.
        if (challenge == null || challenge.getStatus() == ChallengeStatus.DRAFT
                || challenge.getDailyAttendanceReward().signum() <= 0) return;

        Instant start = rewardDate.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = rewardDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        if (challenge.getPublishedAt() != null) {
            if (!challenge.getPublishedAt().isBefore(end)) return;
            if (challenge.getPublishedAt().isAfter(start)) start = challenge.getPublishedAt();
        }
        for (UUID studentId : progressRepository.findStudentsWithCompletedLearningActivity(start, end)) {
            if (attendanceRewardRepository.existsByRewardDateAndStudentId(rewardDate, studentId)) continue;
            BigDecimal amount = challenge.getDailyAttendanceReward();
            WalletTransaction transaction = walletService.creditPromotionalReward(studentId, amount,
                    WalletTransactionType.ATTENDANCE_REWARD, "DAILY_LEARNING_ATTENDANCE", challenge.getId(),
                    "daily-attendance:" + rewardDate + ":" + studentId,
                    "Thưởng điểm danh học tập ngày " + rewardDate);
            attendanceRewardRepository.save(DailyLearningAttendanceReward.builder().id(UUID.randomUUID())
                    .rewardDate(rewardDate).challengeId(challenge.getId()).studentId(studentId).amount(amount)
                    .walletTransactionId(transaction.getId()).awardedAt(Instant.now()).build());
        }
    }
}
