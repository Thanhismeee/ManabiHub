package com.manabihub.challenge.service;

import com.manabihub.challenge.dto.ChallengeAttemptResponse;
import com.manabihub.challenge.dto.MatchCardsRequest;
import com.manabihub.challenge.entity.WeeklyLearningChallenge;
import com.manabihub.challenge.entity.WeeklyLearningChallengeAttempt;
import com.manabihub.challenge.entity.WeeklyLearningChallengeAttemptCard;
import com.manabihub.challenge.entity.WeeklyLearningChallengePair;
import com.manabihub.challenge.enums.ChallengeAttemptState;
import com.manabihub.challenge.enums.ChallengeCardKind;
import com.manabihub.challenge.enums.ChallengeStatus;
import com.manabihub.challenge.repository.WeeklyLearningChallengeAttemptCardRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeAttemptRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengePairRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyChallengeGameServiceTest {
    @Mock private WeeklyLearningChallengeRepository challengeRepository;
    @Mock private WeeklyLearningChallengePairRepository pairRepository;
    @Mock private WeeklyLearningChallengeAttemptRepository attemptRepository;
    @Mock private WeeklyLearningChallengeAttemptCardRepository cardRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @InjectMocks private WeeklyChallengeGameService service;

    @Test
    void start_usesChallengeLockAndReturnsOnlyOpaqueShuffledCards() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        LocalDate weekStart = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        StudentProfile student = StudentProfile.builder().id(studentId).build();
        WeeklyLearningChallenge challenge = WeeklyLearningChallenge.builder()
                .id(challengeId).weekStart(weekStart).status(ChallengeStatus.PUBLISHED)
                .dailyRankedLimit(3).wrongPenaltySeconds(2)
                .dailyAttendanceReward(BigDecimal.ZERO).firstPrize(BigDecimal.ZERO)
                .secondPrize(BigDecimal.ZERO).thirdPrize(BigDecimal.ZERO).build();
        List<WeeklyLearningChallengePair> pairs = IntStream.range(0, 4)
                .mapToObj(index -> WeeklyLearningChallengePair.builder()
                        .id(UUID.randomUUID()).challengeId(challengeId)
                        .prompt("Prompt " + index).answer("Answer " + index).orderIndex(index).build())
                .toList();

        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(pairRepository.findByChallengeIdOrderByOrderIndex(challengeId)).thenReturn(pairs);
        when(attemptRepository.countByChallengeIdAndStudentIdAndRankedDayAndRankedTrue(
                eq(challengeId), eq(studentId), any(LocalDate.class))).thenReturn(0L);

        ChallengeAttemptResponse response = service.start(userId, challengeId);

        assertTrue(response.ranked());
        assertEquals(2, response.remainingRankedAttempts());
        assertEquals(8, response.cards().size());
        assertEquals(8, response.cards().stream().map(card -> card.id()).distinct().count());
        assertEquals(Set.copyOf(IntStream.range(0, 8).boxed().toList()),
                response.cards().stream().map(card -> card.position()).collect(Collectors.toSet()));
        verify(challengeRepository).findByIdForUpdate(challengeId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WeeklyLearningChallengeAttemptCard>> cards = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(cards.capture());
        assertEquals(8, cards.getValue().size());
    }

    @Test
    void match_returnsCompletedResultWhenClientRetriesLastSuccessfulPair() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID pairId = UUID.randomUUID();
        UUID firstCardId = UUID.randomUUID();
        UUID secondCardId = UUID.randomUUID();
        StudentProfile student = StudentProfile.builder().id(studentId).build();
        WeeklyLearningChallenge challenge = WeeklyLearningChallenge.builder()
                .id(challengeId).dailyRankedLimit(3).wrongPenaltySeconds(2).build();
        WeeklyLearningChallengeAttempt attempt = WeeklyLearningChallengeAttempt.builder()
                .id(attemptId).challengeId(challengeId).studentId(studentId)
                .state(ChallengeAttemptState.COMPLETED).ranked(true)
                .rankedDay(LocalDate.now()).matchedPairs(4).penaltyMillis(0)
                .startedAt(Instant.now().minusSeconds(20)).completedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).totalMillis(20000L).build();
        List<WeeklyLearningChallengeAttemptCard> selected = List.of(
                WeeklyLearningChallengeAttemptCard.builder().id(firstCardId).attemptId(attemptId)
                        .pairId(pairId).cardKind(ChallengeCardKind.PROMPT).matched(true).position(0).build(),
                WeeklyLearningChallengeAttemptCard.builder().id(secondCardId).attemptId(attemptId)
                        .pairId(pairId).cardKind(ChallengeCardKind.ANSWER).matched(true).position(1).build());

        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(attemptRepository.findOwnedByIdForUpdate(attemptId, studentId)).thenReturn(Optional.of(attempt));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(cardRepository.findByIdInAndAttemptId(anyCollection(), eq(attemptId))).thenReturn(selected);
        when(cardRepository.findByAttemptIdOrderByPosition(attemptId)).thenReturn(selected);
        when(pairRepository.countByChallengeId(challengeId)).thenReturn(4L);
        when(attemptRepository.countByChallengeIdAndStudentIdAndRankedDayAndRankedTrue(
                eq(challengeId), eq(studentId), any(LocalDate.class))).thenReturn(1L);

        ChallengeAttemptResponse response = service.match(userId, attemptId,
                new MatchCardsRequest(firstCardId, secondCardId));

        assertTrue(response.completed());
        assertEquals(4, response.matchedPairs());
        assertEquals(20000L, response.totalMillis());
        verify(attemptRepository, never()).save(any());
        verify(cardRepository, never()).saveAll(any());
    }

    @Test
    void start_reportsWhetherTheChallengeIsUnpublishedOrFromAnotherWeek() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        LocalDate currentWeek = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        when(studentProfileRepository.findByUser_Id(userId))
                .thenReturn(Optional.of(StudentProfile.builder().id(studentId).build()));

        WeeklyLearningChallenge draft = WeeklyLearningChallenge.builder()
                .id(challengeId).weekStart(currentWeek).status(ChallengeStatus.DRAFT).build();
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(draft));
        BusinessException unpublished = assertThrows(BusinessException.class,
                () -> service.start(userId, challengeId));
        assertEquals(MessageCodes.WEEKLY_CHALLENGE_NOT_PUBLISHED, unpublished.getMessageCode());

        WeeklyLearningChallenge previousWeek = WeeklyLearningChallenge.builder()
                .id(challengeId).weekStart(currentWeek.minusWeeks(1)).status(ChallengeStatus.PUBLISHED).build();
        when(challengeRepository.findByIdForUpdate(challengeId)).thenReturn(Optional.of(previousWeek));
        BusinessException wrongWeek = assertThrows(BusinessException.class,
                () -> service.start(userId, challengeId));
        assertEquals(MessageCodes.WEEKLY_CHALLENGE_NOT_CURRENT_WEEK, wrongWeek.getMessageCode());
    }
}
