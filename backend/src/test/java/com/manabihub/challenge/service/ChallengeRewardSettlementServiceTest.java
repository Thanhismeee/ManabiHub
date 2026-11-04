package com.manabihub.challenge.service;

import com.manabihub.challenge.entity.DailyLearningAttendanceReward;
import com.manabihub.challenge.entity.WeeklyLearningChallenge;
import com.manabihub.challenge.entity.WeeklyLearningChallengeReward;
import com.manabihub.challenge.enums.ChallengeStatus;
import com.manabihub.challenge.repository.DailyLearningAttendanceRewardRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeAttemptRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeRewardRepository;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.wallet.entity.WalletTransaction;
import com.manabihub.wallet.enums.WalletTransactionType;
import com.manabihub.wallet.service.StudentWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ChallengeRewardSettlementServiceTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private WeeklyLearningChallengeRepository challengeRepository;
    @Mock private WeeklyLearningChallengeAttemptRepository attemptRepository;
    @Mock private WeeklyLearningChallengeRewardRepository rewardRepository;
    @Mock private DailyLearningAttendanceRewardRepository attendanceRewardRepository;
    @Mock private LessonBlockProgressRepository progressRepository;
    @Mock private StudentWalletService walletService;
    @Mock private AuditLogRepository auditLogRepository;

    private ChallengeRewardSettlementService service;

    @BeforeEach
    void setUp() {
        service = new ChallengeRewardSettlementService(challengeRepository, attemptRepository,
                rewardRepository, attendanceRewardRepository, progressRepository, walletService, auditLogRepository);
    }

    @Test
    void dailyAttendance_canBeRetriedAfterWeeklyChallengeWasArchived() {
        LocalDate rewardDate = LocalDate.now(BUSINESS_ZONE).minusDays(1);
        LocalDate weekStart = rewardDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        UUID challengeId = UUID.randomUUID();
        UUID rewardedStudent = UUID.randomUUID();
        UUID alreadyRewardedStudent = UUID.randomUUID();
        WeeklyLearningChallenge challenge = challenge(challengeId, weekStart, ChallengeStatus.ARCHIVED);

        when(challengeRepository.findByWeekStartForUpdate(weekStart)).thenReturn(Optional.of(challenge));
        when(progressRepository.findStudentsWithCompletedLearningActivity(any(), any()))
                .thenReturn(List.of(rewardedStudent, alreadyRewardedStudent));
        when(attendanceRewardRepository.existsByRewardDateAndStudentId(rewardDate, rewardedStudent))
                .thenReturn(false);
        when(attendanceRewardRepository.existsByRewardDateAndStudentId(rewardDate, alreadyRewardedStudent))
                .thenReturn(true);
        when(walletService.creditPromotionalReward(eq(rewardedStudent), eq(new BigDecimal("1000")),
                eq(WalletTransactionType.ATTENDANCE_REWARD), eq("DAILY_LEARNING_ATTENDANCE"),
                eq(challengeId), anyString(), anyString()))
                .thenReturn(WalletTransaction.builder().id(UUID.randomUUID()).build());

        service.settleDailyAttendance(rewardDate);

        verify(walletService, times(1)).creditPromotionalReward(any(), any(),
                eq(WalletTransactionType.ATTENDANCE_REWARD), anyString(), any(), anyString(), anyString());
        verify(attendanceRewardRepository).save(any(DailyLearningAttendanceReward.class));
    }

    @Test
    void weeklyReward_isCreditedOnlyAfterTheWeekEndsAndThenArchived() {
        LocalDate currentWeek = LocalDate.now(BUSINESS_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        UUID challengeId = UUID.randomUUID();
        UUID firstStudent = UUID.randomUUID();
        UUID secondStudent = UUID.randomUUID();
        WeeklyLearningChallenge challenge = challenge(challengeId, currentWeek.minusWeeks(1), ChallengeStatus.PUBLISHED);
        WeeklyLearningChallengeAttemptRepository.BestScore first = score(firstStudent, 10_000L);
        WeeklyLearningChallengeAttemptRepository.BestScore second = score(secondStudent, 12_000L);

        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(attemptRepository.findRankedBestScores(challengeId)).thenReturn(List.of(first, second));
        when(rewardRepository.existsByChallengeIdAndStudentId(eq(challengeId), any())).thenReturn(false);
        when(walletService.creditPromotionalReward(any(), any(), eq(WalletTransactionType.GAME_REWARD),
                eq("WEEKLY_CHALLENGE"), eq(challengeId), anyString(), anyString()))
                .thenAnswer(invocation -> WalletTransaction.builder().id(UUID.randomUUID()).build());

        service.settleWeeklyChallenge(challengeId);

        verify(walletService, times(2)).creditPromotionalReward(any(), any(),
                eq(WalletTransactionType.GAME_REWARD), eq("WEEKLY_CHALLENGE"),
                eq(challengeId), anyString(), anyString());
        verify(rewardRepository, times(2)).save(any(WeeklyLearningChallengeReward.class));
        assertNotNull(challenge.getSettledAt());
        assertEquals(ChallengeStatus.ARCHIVED, challenge.getStatus());
        verify(challengeRepository).save(challenge);
    }

    @Test
    void weeklyReward_neverSettlesAnUnfinishedWeek() {
        LocalDate currentWeek = LocalDate.now(BUSINESS_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        UUID challengeId = UUID.randomUUID();
        WeeklyLearningChallenge challenge = challenge(challengeId, currentWeek, ChallengeStatus.PUBLISHED);
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));

        service.settleWeeklyChallenge(challengeId);

        verifyNoInteractions(attemptRepository, rewardRepository, walletService);
        assertNull(challenge.getSettledAt());
        assertEquals(ChallengeStatus.PUBLISHED, challenge.getStatus());
    }

    @Test
    void manualRetry_settlesACompletedWeekAndAuditsTheSystemAdmin() {
        LocalDate completedWeek = LocalDate.now(BUSINESS_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        UUID adminId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        WeeklyLearningChallenge challenge = challenge(challengeId, completedWeek, ChallengeStatus.PUBLISHED);
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(attemptRepository.findRankedBestScores(challengeId)).thenReturn(List.of());

        service.retryWeeklySettlement(adminId, challengeId);

        assertNotNull(challenge.getSettledAt());
        assertEquals(ChallengeStatus.ARCHIVED, challenge.getStatus());
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertEquals(adminId, audit.getValue().getActorAdminId());
        assertEquals("WEEKLY_CHALLENGE_SETTLEMENT_RETRIED", audit.getValue().getAction());
        assertEquals("SETTLED", audit.getValue().getMetadata().get("outcome"));
    }

    @Test
    void dailyAttendance_onlyCountsLearningAfterTheChallengeWasPublished() {
        LocalDate rewardDate = LocalDate.now(BUSINESS_ZONE).minusDays(1);
        LocalDate weekStart = rewardDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        UUID challengeId = UUID.randomUUID();
        WeeklyLearningChallenge challenge = challenge(challengeId, weekStart, ChallengeStatus.PUBLISHED);
        Instant publishedAt = rewardDate.atTime(13, 30).atZone(BUSINESS_ZONE).toInstant();
        Instant dayEnd = rewardDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        challenge.setPublishedAt(publishedAt);
        when(challengeRepository.findByWeekStartForUpdate(weekStart)).thenReturn(Optional.of(challenge));
        when(progressRepository.findStudentsWithCompletedLearningActivity(publishedAt, dayEnd))
                .thenReturn(List.of());

        service.settleDailyAttendance(rewardDate);

        verify(progressRepository).findStudentsWithCompletedLearningActivity(publishedAt, dayEnd);
        verifyNoInteractions(walletService);
    }

    private WeeklyLearningChallenge challenge(UUID id, LocalDate weekStart, ChallengeStatus status) {
        return WeeklyLearningChallenge.builder()
                .id(id).weekStart(weekStart).title("Challenge").description("Description")
                .jlptLevel("N5").status(status).dailyRankedLimit(3).wrongPenaltySeconds(2)
                .dailyAttendanceReward(new BigDecimal("1000"))
                .firstPrize(new BigDecimal("30000"))
                .secondPrize(new BigDecimal("20000"))
                .thirdPrize(new BigDecimal("10000"))
                .publishedAt(weekStart.atStartOfDay(BUSINESS_ZONE).toInstant())
                .build();
    }

    private WeeklyLearningChallengeAttemptRepository.BestScore score(UUID studentId, long millis) {
        return new WeeklyLearningChallengeAttemptRepository.BestScore() {
            @Override public UUID getStudentId() { return studentId; }
            @Override public Long getBestMillis() { return millis; }
        };
    }
}
