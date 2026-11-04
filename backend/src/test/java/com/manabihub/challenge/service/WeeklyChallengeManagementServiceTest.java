package com.manabihub.challenge.service;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.challenge.dto.ChallengePairRequest;
import com.manabihub.challenge.dto.UpsertWeeklyChallengeRequest;
import com.manabihub.challenge.entity.WeeklyLearningChallenge;
import com.manabihub.challenge.enums.ChallengeStatus;
import com.manabihub.challenge.repository.DailyLearningAttendanceRewardRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeAttemptRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengePairRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeRewardRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.exception.ValidationBusinessException;
import com.manabihub.course.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyChallengeManagementServiceTest {
    @Mock private WeeklyLearningChallengeRepository challengeRepository;
    @Mock private WeeklyLearningChallengePairRepository pairRepository;
    @Mock private WeeklyLearningChallengeAttemptRepository attemptRepository;
    @Mock private WeeklyLearningChallengeRewardRepository rewardRepository;
    @Mock private DailyLearningAttendanceRewardRepository attendanceRewardRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private AuditLogRepository auditLogRepository;

    private WeeklyChallengeManagementService service;
    private UUID managerId;

    @BeforeEach
    void setUp() {
        service = new WeeklyChallengeManagementService(challengeRepository, pairRepository,
                attemptRepository, rewardRepository, attendanceRewardRepository,
                courseRepository, auditLogRepository);
        managerId = UUID.randomUUID();
        when(courseRepository.hasAdminRole(managerId, List.of("COURSE_MANAGER"))).thenReturn(true);
    }

    @Test
    void unpublish_isBlockedAfterDailyAttendanceMoneyWasAwarded() {
        UUID challengeId = UUID.randomUUID();
        WeeklyLearningChallenge challenge = challenge(challengeId, ChallengeStatus.PUBLISHED);
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(attemptRepository.existsByChallengeId(challengeId)).thenReturn(false);
        when(rewardRepository.existsByChallengeId(challengeId)).thenReturn(false);
        when(attendanceRewardRepository.existsByChallengeId(challengeId)).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.unpublish(managerId, challengeId));

        assertEquals(MessageCodes.WEEKLY_CHALLENGE_CONFLICT, error.getMessageCode());
        assertEquals(ChallengeStatus.PUBLISHED, challenge.getStatus());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void delete_isBlockedForDraftWithRecordedRewardHistory() {
        UUID challengeId = UUID.randomUUID();
        WeeklyLearningChallenge challenge = challenge(challengeId, ChallengeStatus.DRAFT);
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(attemptRepository.existsByChallengeId(challengeId)).thenReturn(false);
        when(rewardRepository.existsByChallengeId(challengeId)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.delete(managerId, challengeId));

        verify(challengeRepository, never()).delete(any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void create_writesDatabaseCompatibleInternalAdminAuditActorType() {
        LocalDate weekStart = LocalDate.of(2026, 8, 17);
        UpsertWeeklyChallengeRequest request = new UpsertWeeklyChallengeRequest(
                weekStart,
                "Thử thách Kanji tuần",
                "Ghép từ Kanji với cách đọc tương ứng.",
                "N5",
                3,
                2,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(30000),
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(10000),
                List.of(
                        new ChallengePairRequest("日", "mặt trời"),
                        new ChallengePairRequest("月", "mặt trăng"),
                        new ChallengePairRequest("火", "lửa"),
                        new ChallengePairRequest("水", "nước")
                ));
        when(challengeRepository.findByWeekStart(weekStart)).thenReturn(Optional.empty());
        when(pairRepository.findByChallengeIdOrderByOrderIndex(any(UUID.class))).thenReturn(List.of());

        service.create(managerId, request);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertEquals("INTERNAL_ADMIN", auditLog.getActorType());
        assertEquals(managerId, auditLog.getActorAdminId());
        assertEquals("COURSE_MANAGER", auditLog.getActorRoleCode());
        assertEquals("WEEKLY_CHALLENGE_CREATED", auditLog.getAction());
    }

    @Test
    void create_rejectsAscendingPrizesWithTheStandardValidationCode() {
        UpsertWeeklyChallengeRequest request = new UpsertWeeklyChallengeRequest(
                LocalDate.of(2026, 8, 17), "Thử thách Kanji tuần", "Ghép từ Kanji với cách đọc.",
                "N5", 3, 2, new BigDecimal("1000"), new BigDecimal("100000"),
                new BigDecimal("200000"), new BigDecimal("50000"), List.of(
                new ChallengePairRequest("月", "mặt trăng"),
                new ChallengePairRequest("火", "lửa"),
                new ChallengePairRequest("水", "nước"),
                new ChallengePairRequest("木", "cây")
        ));

        ValidationBusinessException error = assertThrows(ValidationBusinessException.class,
                () -> service.create(managerId, request));

        assertEquals(MessageCodes.VALIDATION_FAILED, error.getMessageCode());
        assertEquals("Mức thưởng phải giảm dần theo hạng 1, 2, 3", error.getMessage());
        assertEquals("secondPrize", error.getValidationErrors().getFirst().code());
        verifyNoInteractions(challengeRepository);
    }

    private WeeklyLearningChallenge challenge(UUID id, ChallengeStatus status) {
        return WeeklyLearningChallenge.builder()
                .id(id)
                .weekStart(LocalDate.of(2026, 8, 10))
                .status(status)
                .build();
    }
}
