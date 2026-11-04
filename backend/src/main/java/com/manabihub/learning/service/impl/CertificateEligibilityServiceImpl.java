package com.manabihub.learning.service.impl;

import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.finaltest.repository.FinalTestRepository;
import com.manabihub.learning.dto.response.CertificateEligibilityResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.LessonBlockProgress;
import com.manabihub.learning.entity.QuizAttempt;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.repository.FinalTestAttemptRepository;
import com.manabihub.learning.repository.QuizAttemptRepository;
import com.manabihub.learning.service.CertificateEligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateEligibilityServiceImpl implements CertificateEligibilityService {

    private static final int EXERCISE_SCORE_THRESHOLD = 85;

    private final QuizAttemptRepository quizAttemptRepository;
    private final FinalTestRepository finalTestRepository;
    private final FinalTestAttemptRepository finalTestAttemptRepository;

    @Override
    public CertificateEligibilityResponse evaluate(
            Enrollment enrollment,
            List<LessonBlock> blocks,
            Map<UUID, LessonBlockProgress> progressByBlockId
    ) {
        boolean progressComplete = !blocks.isEmpty()
                && blocks.stream().allMatch(block -> isCompleted(progressByBlockId.get(block.getId())));

        List<LessonBlock> writingBlocks = blocks.stream()
                .filter(block -> block.getType() == LessonBlockType.WRITING)
                .toList();
        boolean requiredAssignmentsComplete = writingBlocks.stream()
                .allMatch(block -> isCompleted(progressByBlockId.get(block.getId())));

        List<LessonBlock> quizBlocks = blocks.stream()
                .filter(block -> block.getType() == LessonBlockType.QUIZ)
                .toList();
        Map<UUID, BigDecimal> bestQuizScores = quizAttemptRepository.findByEnrollmentId(enrollment.getId())
                .stream()
                .collect(Collectors.toMap(
                        attempt -> attempt.getLessonBlock().getId(),
                        QuizAttempt::getScore,
                        (first, second) -> Comparator.<BigDecimal>naturalOrder().compare(first, second) >= 0
                                ? first
                                : second
                ));
        boolean everyQuizScored = quizBlocks.stream()
                .allMatch(block -> bestQuizScores.containsKey(block.getId()));
        BigDecimal exerciseAverageScore = quizBlocks.isEmpty()
                ? null
                : quizBlocks.stream()
                        .map(block -> bestQuizScores.getOrDefault(block.getId(), BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(quizBlocks.size()), 2, RoundingMode.HALF_UP);
        boolean exerciseScoreSatisfied = quizBlocks.isEmpty()
                || everyQuizScored
                && exerciseAverageScore.compareTo(BigDecimal.valueOf(EXERCISE_SCORE_THRESHOLD)) >= 0;

        boolean finalTestPassed = finalTestRepository.findByCourseId(enrollment.getCourse().getId())
                .map(finalTest -> finalTestAttemptRepository.existsByEnrollmentIdAndFinalTestIdAndPassedTrue(
                        enrollment.getId(),
                        finalTest.getId()
                ))
                .orElse(false);

        List<String> reasons = new ArrayList<>();
        if (!progressComplete) {
            reasons.add("PROGRESS_INCOMPLETE");
        }
        if (!requiredAssignmentsComplete) {
            reasons.add("ASSIGNMENTS_INCOMPLETE");
        }
        if (!exerciseScoreSatisfied) {
            reasons.add("EXERCISE_AVERAGE_BELOW_85");
        }
        if (!finalTestPassed) {
            reasons.add("FINAL_TEST_NOT_PASSED");
        }

        return new CertificateEligibilityResponse(
                reasons.isEmpty(),
                progressComplete,
                requiredAssignmentsComplete,
                exerciseScoreSatisfied,
                exerciseAverageScore,
                EXERCISE_SCORE_THRESHOLD,
                finalTestPassed,
                List.copyOf(reasons)
        );
    }

    private boolean isCompleted(LessonBlockProgress progress) {
        return progress != null && progress.getStatus() == LessonProgressStatus.COMPLETED;
    }
}
