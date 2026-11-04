package com.manabihub.learning.service.impl;

import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.finaltest.repository.FinalTestRepository;
import com.manabihub.learning.dto.response.CertificateEligibilityResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.LessonBlockProgress;
import com.manabihub.learning.entity.QuizAttempt;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.repository.FinalTestAttemptRepository;
import com.manabihub.learning.repository.QuizAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateEligibilityServiceImplTest {

    @Mock
    private QuizAttemptRepository quizAttemptRepository;

    @Mock
    private FinalTestRepository finalTestRepository;

    @Mock
    private FinalTestAttemptRepository finalTestAttemptRepository;

    @InjectMocks
    private CertificateEligibilityServiceImpl service;

    private Enrollment enrollment;
    private LessonBlock videoBlock;
    private LessonBlock writingBlock;
    private LessonBlock quizBlock;
    private FinalTest finalTest;

    @BeforeEach
    void setUp() {
        Course course = Course.builder().id(UUID.randomUUID()).build();
        enrollment = Enrollment.builder().id(UUID.randomUUID()).course(course).build();
        videoBlock = block(LessonBlockType.VIDEO);
        writingBlock = block(LessonBlockType.WRITING);
        quizBlock = block(LessonBlockType.QUIZ);
        finalTest = FinalTest.builder().id(UUID.randomUUID()).course(course).build();
    }

    @Test
    void evaluate_returnsEligibleWhenEveryRulePasses() {
        List<LessonBlock> blocks = List.of(videoBlock, writingBlock, quizBlock);
        Map<UUID, LessonBlockProgress> progress = completedProgress(blocks);
        when(quizAttemptRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of(
                attempt(quizBlock, "82"),
                attempt(quizBlock, "90")
        ));
        mockFinalTestPassed(true);

        CertificateEligibilityResponse result = service.evaluate(enrollment, blocks, progress);

        assertTrue(result.eligible());
        assertEquals(new BigDecimal("90.00"), result.exerciseAverageScore());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void evaluate_usesBestAttemptPerQuizAndRequiresAverage85() {
        LessonBlock secondQuiz = block(LessonBlockType.QUIZ);
        List<LessonBlock> blocks = List.of(quizBlock, secondQuiz);
        Map<UUID, LessonBlockProgress> progress = completedProgress(blocks);
        when(quizAttemptRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of(
                attempt(quizBlock, "70"),
                attempt(quizBlock, "90"),
                attempt(secondQuiz, "70")
        ));
        mockFinalTestPassed(true);

        CertificateEligibilityResponse result = service.evaluate(enrollment, blocks, progress);

        assertFalse(result.eligible());
        assertEquals(new BigDecimal("80.00"), result.exerciseAverageScore());
        assertTrue(result.reasons().contains("EXERCISE_AVERAGE_BELOW_85"));
    }

    @Test
    void evaluate_blocksWhenARequiredQuizHasNoAttempt() {
        List<LessonBlock> blocks = List.of(quizBlock);
        when(quizAttemptRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of());
        mockFinalTestPassed(true);

        CertificateEligibilityResponse result = service.evaluate(
                enrollment,
                blocks,
                completedProgress(blocks)
        );

        assertFalse(result.exerciseScoreSatisfied());
        assertEquals(new BigDecimal("0.00"), result.exerciseAverageScore());
    }

    @Test
    void evaluate_treatsExerciseScoreAsNotApplicableWhenCourseHasNoQuiz() {
        List<LessonBlock> blocks = List.of(videoBlock, writingBlock);
        when(quizAttemptRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of());
        mockFinalTestPassed(true);

        CertificateEligibilityResponse result = service.evaluate(
                enrollment,
                blocks,
                completedProgress(blocks)
        );

        assertTrue(result.eligible());
        assertTrue(result.exerciseScoreSatisfied());
        assertNull(result.exerciseAverageScore());
    }

    @Test
    void evaluate_reportsIncompleteProgressAssignmentAndFinalTest() {
        List<LessonBlock> blocks = List.of(videoBlock, writingBlock);
        when(quizAttemptRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of());
        when(finalTestRepository.findByCourseId(enrollment.getCourse().getId()))
                .thenReturn(Optional.of(finalTest));
        when(finalTestAttemptRepository.existsByEnrollmentIdAndFinalTestIdAndPassedTrue(
                enrollment.getId(),
                finalTest.getId()
        )).thenReturn(false);

        CertificateEligibilityResponse result = service.evaluate(enrollment, blocks, Map.of());

        assertFalse(result.eligible());
        assertTrue(result.reasons().contains("PROGRESS_INCOMPLETE"));
        assertTrue(result.reasons().contains("ASSIGNMENTS_INCOMPLETE"));
        assertTrue(result.reasons().contains("FINAL_TEST_NOT_PASSED"));
    }

    private void mockFinalTestPassed(boolean passed) {
        when(finalTestRepository.findByCourseId(enrollment.getCourse().getId()))
                .thenReturn(Optional.of(finalTest));
        when(finalTestAttemptRepository.existsByEnrollmentIdAndFinalTestIdAndPassedTrue(
                enrollment.getId(),
                finalTest.getId()
        )).thenReturn(passed);
    }

    private LessonBlock block(LessonBlockType type) {
        return LessonBlock.builder().id(UUID.randomUUID()).type(type).build();
    }

    private Map<UUID, LessonBlockProgress> completedProgress(List<LessonBlock> blocks) {
        return blocks.stream().collect(java.util.stream.Collectors.toMap(
                LessonBlock::getId,
                block -> LessonBlockProgress.builder()
                        .lessonBlockId(block.getId())
                        .status(LessonProgressStatus.COMPLETED)
                        .build()
        ));
    }

    private QuizAttempt attempt(LessonBlock block, String score) {
        return QuizAttempt.builder()
                .lessonBlock(block)
                .score(new BigDecimal(score))
                .build();
    }
}
