package com.manabihub.challenge.service;

import com.manabihub.challenge.dto.*;
import com.manabihub.challenge.entity.*;
import com.manabihub.challenge.enums.*;
import com.manabihub.challenge.repository.*;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WeeklyChallengeGameService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(20);
    private final WeeklyLearningChallengeRepository challengeRepository;
    private final WeeklyLearningChallengePairRepository pairRepository;
    private final WeeklyLearningChallengeAttemptRepository attemptRepository;
    private final WeeklyLearningChallengeAttemptCardRepository cardRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public WeeklyChallengeResponse current(UUID userId) {
        StudentProfile student = requireStudent(userId);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyLearningChallenge challenge = challengeRepository.findByWeekStartAndStatus(weekStart, ChallengeStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(MessageCodes.WEEKLY_CHALLENGE_NOT_AVAILABLE,
                        "Tuần này chưa có thử thách được công khai", HttpStatus.NOT_FOUND));
        long attempts = attemptRepository.countByChallengeIdAndStudentIdAndRankedDayAndRankedTrue(
                challenge.getId(), student.getId(), today);
        Long best = attemptRepository
                .findFirstByChallengeIdAndStudentIdAndStateAndRankedTrueOrderByTotalMillisAsc(
                        challenge.getId(), student.getId(), ChallengeAttemptState.COMPLETED)
                .map(WeeklyLearningChallengeAttempt::getTotalMillis).orElse(null);
        return new WeeklyChallengeResponse(challenge.getId(), weekStart, weekStart.plusDays(6), challenge.getTitle(),
                challenge.getDescription(), challenge.getJlptLevel(), challenge.getStatus(),
                challenge.getDailyRankedLimit(), challenge.getWrongPenaltySeconds(),
                challenge.getDailyAttendanceReward(), challenge.getFirstPrize(), challenge.getSecondPrize(),
                challenge.getThirdPrize(), List.of(), challenge.getPublishedAt(), challenge.getSettledAt(), best, attempts);
    }

    @Transactional
    public ChallengeAttemptResponse start(UUID userId, UUID challengeId) {
        StudentProfile student = requireStudent(userId);
        WeeklyLearningChallenge challenge = requireCurrentPublished(challengeId);
        List<WeeklyLearningChallengePair> pairs = pairRepository.findByChallengeIdOrderByOrderIndex(challengeId);
        if (pairs.size() < 4) throw invalid("Thử thách chưa đủ nội dung để bắt đầu");

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        long rankedToday = attemptRepository.countByChallengeIdAndStudentIdAndRankedDayAndRankedTrue(
                challengeId, student.getId(), today);
        boolean ranked = rankedToday < challenge.getDailyRankedLimit();
        Instant now = Instant.now();
        WeeklyLearningChallengeAttempt attempt = WeeklyLearningChallengeAttempt.builder()
                .id(UUID.randomUUID()).challengeId(challengeId).studentId(student.getId())
                .state(ChallengeAttemptState.IN_PROGRESS).ranked(ranked).rankedDay(today)
                .matchedPairs(0).penaltyMillis(0).startedAt(now).expiresAt(now.plus(ATTEMPT_TTL)).build();
        attemptRepository.save(attempt);

        List<WeeklyLearningChallengeAttemptCard> cards = new ArrayList<>();
        for (WeeklyLearningChallengePair pair : pairs) {
            cards.add(card(attempt.getId(), pair.getId(), ChallengeCardKind.PROMPT, pair.getPrompt()));
            cards.add(card(attempt.getId(), pair.getId(), ChallengeCardKind.ANSWER, pair.getAnswer()));
        }
        Collections.shuffle(cards, secureRandom);
        for (int i = 0; i < cards.size(); i++) cards.get(i).setPosition(i);
        cardRepository.saveAll(cards);
        return mapAttempt(attempt, cards, challenge.getDailyRankedLimit() - (int) rankedToday - (ranked ? 1 : 0), pairs.size());
    }

    @Transactional
    public ChallengeAttemptResponse match(UUID userId, UUID attemptId, MatchCardsRequest request) {
        StudentProfile student = requireStudent(userId);
        WeeklyLearningChallengeAttempt attempt = attemptRepository.findOwnedByIdForUpdate(attemptId, student.getId())
                .orElseThrow(() -> new BusinessException(MessageCodes.WEEKLY_CHALLENGE_ATTEMPT_NOT_FOUND,
                        "Không tìm thấy lượt chơi", HttpStatus.NOT_FOUND));
        WeeklyLearningChallenge challenge = challengeRepository.findById(attempt.getChallengeId())
                .orElseThrow(() -> invalid("Thử thách không còn tồn tại"));
        expireIfNeeded(attempt);
        if (request.firstCardId().equals(request.secondCardId())) throw invalid("Phải chọn hai thẻ khác nhau");
        List<WeeklyLearningChallengeAttemptCard> selected = cardRepository.findByIdInAndAttemptId(
                List.of(request.firstCardId(), request.secondCardId()), attemptId);
        if (selected.size() != 2) throw invalid("Thẻ không hợp lệ hoặc không thuộc lượt chơi này");
        WeeklyLearningChallengeAttemptCard first = selected.get(0);
        WeeklyLearningChallengeAttemptCard second = selected.get(1);
        boolean matched = first.getPairId().equals(second.getPairId()) && first.getCardKind() != second.getCardKind();
        boolean bothAlreadyMatched = first.isMatched() && second.isMatched();
        if (bothAlreadyMatched && matched) {
            return currentAttemptResponse(attempt, challenge, student.getId());
        }
        if (attempt.getState() != ChallengeAttemptState.IN_PROGRESS) {
            throw conflict("Lượt chơi đã kết thúc hoặc hết hạn");
        }
        if (first.isMatched() || second.isMatched()) {
            throw invalid("Thẻ không hợp lệ hoặc đã được ghép");
        }
        if (matched) {
            first.setMatched(true);
            second.setMatched(true);
            cardRepository.saveAll(selected);
            attempt.setMatchedPairs(attempt.getMatchedPairs() + 1);
        } else {
            attempt.setPenaltyMillis(attempt.getPenaltyMillis() + challenge.getWrongPenaltySeconds() * 1000L);
        }

        int totalPairs = (int) pairRepository.countByChallengeId(challenge.getId());
        if (attempt.getMatchedPairs() == totalPairs) {
            Instant now = Instant.now();
            attempt.setCompletedAt(now);
            attempt.setState(ChallengeAttemptState.COMPLETED);
            attempt.setTotalMillis(Duration.between(attempt.getStartedAt(), now).toMillis() + attempt.getPenaltyMillis());
        }
        attemptRepository.save(attempt);
        return currentAttemptResponse(attempt, challenge, student.getId());
    }

    private ChallengeAttemptResponse currentAttemptResponse(WeeklyLearningChallengeAttempt attempt,
                                                            WeeklyLearningChallenge challenge,
                                                            UUID studentId) {
        long rankedToday = attemptRepository.countByChallengeIdAndStudentIdAndRankedDayAndRankedTrue(
                challenge.getId(), studentId, LocalDate.now(BUSINESS_ZONE));
        int totalPairs = (int) pairRepository.countByChallengeId(challenge.getId());
        return mapAttempt(attempt, cardRepository.findByAttemptIdOrderByPosition(attempt.getId()),
                Math.max(0, challenge.getDailyRankedLimit() - (int) rankedToday), totalPairs);
    }

    private WeeklyLearningChallenge requireCurrentPublished(UUID id) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // Serializing starts on the challenge row prevents concurrent requests
        // from both observing the same remaining daily ranked slot.
        WeeklyLearningChallenge challenge = challengeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(MessageCodes.WEEKLY_CHALLENGE_NOT_FOUND, "Không tìm thấy thử thách", HttpStatus.NOT_FOUND));
        if (challenge.getStatus() != ChallengeStatus.PUBLISHED) {
            throw new BusinessException(MessageCodes.WEEKLY_CHALLENGE_NOT_PUBLISHED,
                    "Thử thách chưa được công khai", HttpStatus.CONFLICT);
        }
        if (!challenge.getWeekStart().equals(weekStart)) {
            throw new BusinessException(MessageCodes.WEEKLY_CHALLENGE_NOT_CURRENT_WEEK,
                    "Thử thách không thuộc tuần hiện tại", HttpStatus.CONFLICT);
        }
        return challenge;
    }

    private void expireIfNeeded(WeeklyLearningChallengeAttempt attempt) {
        if (attempt.getState() == ChallengeAttemptState.IN_PROGRESS && !attempt.getExpiresAt().isAfter(Instant.now())) {
            attempt.setState(ChallengeAttemptState.EXPIRED);
            attemptRepository.save(attempt);
        }
    }

    private WeeklyLearningChallengeAttemptCard card(UUID attemptId, UUID pairId, ChallengeCardKind kind, String value) {
        return WeeklyLearningChallengeAttemptCard.builder().id(UUID.randomUUID()).attemptId(attemptId)
                .pairId(pairId).cardKind(kind).displayValue(value).matched(false).build();
    }

    private ChallengeAttemptResponse mapAttempt(WeeklyLearningChallengeAttempt attempt,
                                                List<WeeklyLearningChallengeAttemptCard> cards,
                                                int remaining, int totalPairs) {
        return new ChallengeAttemptResponse(attempt.getId(), attempt.isRanked(), Math.max(0, remaining),
                cards.stream().map(card -> new ChallengeAttemptResponse.ChallengeCardResponse(
                        card.getId(), card.getDisplayValue(), card.getPosition(), card.isMatched())).toList(),
                attempt.getMatchedPairs(), totalPairs, attempt.getPenaltyMillis(), attempt.getTotalMillis(),
                attempt.getExpiresAt(), attempt.getState() == ChallengeAttemptState.COMPLETED);
    }

    private StudentProfile requireStudent(UUID userId) {
        return studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(MessageCodes.LEARNING_STUDENT_PROFILE_NOT_FOUND, "Không tìm thấy hồ sơ học viên", HttpStatus.NOT_FOUND));
    }

    private BusinessException invalid(String message) { return new BusinessException(MessageCodes.WEEKLY_CHALLENGE_INVALID, message, HttpStatus.BAD_REQUEST); }
    private BusinessException conflict(String message) { return new BusinessException(MessageCodes.WEEKLY_CHALLENGE_CONFLICT, message, HttpStatus.CONFLICT); }
}
