package com.manabihub.learning.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.finaltest.entity.FinalTestChoice;
import com.manabihub.finaltest.entity.FinalTestQuestion;
import com.manabihub.finaltest.repository.FinalTestRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.dto.request.FinalTestSubmissionRequest;
import com.manabihub.learning.dto.request.QuizSubmissionRequest;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.FinalTestAttempt;
import com.manabihub.learning.entity.LessonBlockProgress;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.enums.FinalTestAttemptStatus;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.FinalTestAttemptRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.repository.QuizAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAssessmentServiceImplTest {

    @Mock private CourseRepository courseRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private FinalTestRepository finalTestRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private LessonBlockProgressRepository lessonBlockProgressRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private FinalTestAttemptRepository finalTestAttemptRepository;
    @Mock private CurrentUserService currentUserService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StudentAssessmentServiceImpl service;

    private UUID userId;
    private Course course;
    private CourseModule module;
    private LessonBlock quizBlock;
    private LessonBlock textBlock;
    private StudentProfile student;
    private Enrollment enrollment;
    private FinalTest finalTest;
    private FinalTestQuestion finalQuestion;
    private FinalTestChoice correctChoice;
    private FinalTestChoice wrongChoice;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        course = Course.builder()
                .id(UUID.randomUUID())
                .title("N3 Course")
                .modules(new ArrayList<>())
                .build();
        module = CourseModule.builder()
                .id(UUID.randomUUID())
                .title("Module")
                .orderIndex(1)
                .blocks(new ArrayList<>())
                .build();
        course.addModule(module);

        quizBlock = LessonBlock.builder()
                .id(UUID.randomUUID())
                .module(module)
                .type(LessonBlockType.QUIZ)
                .title("Quiz")
                .orderIndex(1)
                .quizItemsJson("""
                        [
                          {"question":"Q1","options":["A","B"],"answer":"A"},
                          {"question":"Q2","options":["C","D"],"answer":"D"}
                        ]
                        """)
                .build();
        textBlock = LessonBlock.builder()
                .id(UUID.randomUUID())
                .module(module)
                .type(LessonBlockType.TEXT)
                .title("Reading")
                .orderIndex(2)
                .content("Content")
                .build();
        module.getBlocks().add(quizBlock);
        module.getBlocks().add(textBlock);

        student = StudentProfile.builder().id(UUID.randomUUID()).build();
        enrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .student(student)
                .course(course)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        finalTest = FinalTest.builder()
                .id(UUID.randomUUID())
                .course(course)
                .timeLimitMinutes(30)
                .passingScore(80)
                .maxRetakes(1)
                .questions(new ArrayList<>())
                .build();
        finalQuestion = FinalTestQuestion.builder()
                .id(UUID.randomUUID())
                .finalTest(finalTest)
                .content("Final question")
                .explanation("Because A is correct.")
                .orderIndex(1)
                .choices(new ArrayList<>())
                .build();
        correctChoice = FinalTestChoice.builder()
                .id(UUID.randomUUID())
                .question(finalQuestion)
                .content("A")
                .isCorrect(true)
                .orderIndex(1)
                .build();
        wrongChoice = FinalTestChoice.builder()
                .id(UUID.randomUUID())
                .question(finalQuestion)
                .content("B")
                .isCorrect(false)
                .orderIndex(2)
                .build();
        finalQuestion.getChoices().add(correctChoice);
        finalQuestion.getChoices().add(wrongChoice);
        finalTest.getQuestions().add(finalQuestion);

        lenient().when(currentUserService.getCurrentUserId()).thenReturn(userId);
        lenient().when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        lenient().when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        lenient().when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                .thenReturn(Optional.of(enrollment));
        lenient().when(enrollmentRepository.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
    }

    @Test
    void submitQuiz_whenPassing_persistsAttemptAndCompletesBlock() {
        when(lessonBlockRepository.findById(quizBlock.getId())).thenReturn(Optional.of(quizBlock));
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(
                enrollment.getId(),
                quizBlock.getId()
        )).thenReturn(Optional.empty());

        var result = service.submitQuiz(
                quizBlock.getId(),
                new QuizSubmissionRequest(List.of("A", "D"))
        );

        assertTrue(result.passed());
        assertEquals(0, result.score().compareTo(new java.math.BigDecimal("100.00")));
        assertEquals(LessonProgressStatus.COMPLETED, result.progressStatus());
        assertEquals("A", result.feedback().getFirst().correctAnswer());
        verify(quizAttemptRepository).save(any());
        ArgumentCaptor<LessonBlockProgress> progressCaptor =
                ArgumentCaptor.forClass(LessonBlockProgress.class);
        verify(lessonBlockProgressRepository).save(progressCaptor.capture());
        assertEquals(LessonProgressStatus.COMPLETED, progressCaptor.getValue().getStatus());
    }

    @Test
    void submitQuiz_whenAnswerDoesNotBelongToQuestion_rejectsRequest() {
        when(lessonBlockRepository.findById(quizBlock.getId())).thenReturn(Optional.of(quizBlock));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.submitQuiz(
                        quizBlock.getId(),
                        new QuizSubmissionRequest(List.of("NOT_AN_OPTION", "D"))
                )
        );

        assertEquals(MessageCodes.LEARNING_INVALID_QUIZ_ANSWERS, exception.getMessageCode());
        verify(quizAttemptRepository, never()).save(any());
    }

    @Test
    void getFinalTestEligibility_whenLessonsIncomplete_returnsReason() {
        when(finalTestRepository.findByCourseId(course.getId())).thenReturn(Optional.of(finalTest));
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollment.getId()))
                .thenReturn(List.of(completedProgress(quizBlock)));
        when(finalTestAttemptRepository.countByEnrollmentIdAndFinalTestId(
                enrollment.getId(),
                finalTest.getId()
        )).thenReturn(0L);

        var result = service.getFinalTestEligibility(course.getId());

        assertFalse(result.eligible());
        assertEquals("LESSONS_INCOMPLETE", result.reason());
        assertEquals(1, result.completedLessons());
        assertEquals(2, result.totalLessons());
    }

    @Test
    void getFinalTestEligibility_ignoresModerationHiddenLessons() {
        textBlock.setModerationHidden(true);
        when(finalTestRepository.findByCourseId(course.getId())).thenReturn(Optional.of(finalTest));
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollment.getId()))
                .thenReturn(List.of(completedProgress(quizBlock)));
        when(finalTestAttemptRepository.countByEnrollmentIdAndFinalTestId(
                enrollment.getId(),
                finalTest.getId()
        )).thenReturn(0L);

        var result = service.getFinalTestEligibility(course.getId());

        assertTrue(result.eligible());
        assertEquals(1, result.completedLessons());
        assertEquals(1, result.totalLessons());
    }

    @Test
    void startFinalTest_returnsQuestionsWithoutCorrectnessMetadata() {
        mockAllLessonsCompleted();
        when(finalTestRepository.findByCourseId(course.getId())).thenReturn(Optional.of(finalTest));
        when(finalTestAttemptRepository.countByEnrollmentIdAndFinalTestId(
                enrollment.getId(),
                finalTest.getId()
        )).thenReturn(0L, 0L, 1L);
        when(finalTestAttemptRepository.findFirstByEnrollmentIdAndFinalTestIdAndStatusOrderByStartedAtDesc(
                enrollment.getId(),
                finalTest.getId(),
                FinalTestAttemptStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(finalTestAttemptRepository.save(any())).thenAnswer(invocation -> {
            FinalTestAttempt attempt = invocation.getArgument(0);
            attempt.setId(UUID.randomUUID());
            return attempt;
        });

        var result = service.startFinalTest(course.getId());

        assertNotNull(result.attemptId());
        assertEquals(1, result.questions().size());
        assertEquals(List.of("A", "B"), result.questions().getFirst().choices()
                .stream()
                .map(choice -> choice.content())
                .toList());
        assertEquals(1, result.attemptsRemaining());
    }

    @Test
    void submitFinalTest_whenFailed_persistsResultAndBlocksCertificate() {
        FinalTestAttempt attempt = activeAttempt();
        when(finalTestAttemptRepository.findOwnedByIdForUpdate(attempt.getId(), enrollment.getId()))
                .thenReturn(Optional.of(attempt));

        var result = service.submitFinalTest(
                course.getId(),
                new FinalTestSubmissionRequest(
                        attempt.getId(),
                        List.of(new FinalTestSubmissionRequest.FinalTestAnswer(
                                finalQuestion.getId(),
                                List.of(wrongChoice.getId())
                        ))
                )
        );

        assertFalse(result.passed());
        assertTrue(result.certificateBlocked());
        assertEquals(EnrollmentStatus.ACTIVE, enrollment.getStatus());
        assertEquals(FinalTestAttemptStatus.SUBMITTED, attempt.getStatus());
        verify(enrollmentRepository, never()).save(enrollment);
    }

    @Test
    void submitFinalTest_whenPassed_completesEnrollment() {
        FinalTestAttempt attempt = activeAttempt();
        when(finalTestAttemptRepository.findOwnedByIdForUpdate(attempt.getId(), enrollment.getId()))
                .thenReturn(Optional.of(attempt));

        var result = service.submitFinalTest(
                course.getId(),
                new FinalTestSubmissionRequest(
                        attempt.getId(),
                        List.of(new FinalTestSubmissionRequest.FinalTestAnswer(
                                finalQuestion.getId(),
                                List.of(correctChoice.getId())
                        ))
                )
        );

        assertTrue(result.passed());
        assertFalse(result.certificateBlocked());
        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        assertNotNull(enrollment.getCompletedAt());
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void terminateFinalTestAttempt_marksActiveAttemptAsViolationAndConsumesAttempt() {
        FinalTestAttempt attempt = activeAttempt();
        when(finalTestAttemptRepository.findOwnedByIdForUpdate(attempt.getId(), enrollment.getId()))
                .thenReturn(Optional.of(attempt));

        service.terminateFinalTestAttempt(course.getId(), attempt.getId());

        assertEquals(FinalTestAttemptStatus.TERMINATED_FOR_VIOLATION, attempt.getStatus());
        assertFalse(attempt.getPassed());
        assertNotNull(attempt.getSubmittedAt());
        verify(finalTestAttemptRepository).save(attempt);
    }

    @Test
    void submitFinalTest_whenAttemptBelongsToAnotherStudent_returnsNotFound() {
        UUID attemptId = UUID.randomUUID();
        when(finalTestAttemptRepository.findOwnedByIdForUpdate(attemptId, enrollment.getId()))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.submitFinalTest(
                        course.getId(),
                        new FinalTestSubmissionRequest(
                                attemptId,
                                List.of(new FinalTestSubmissionRequest.FinalTestAnswer(
                                        finalQuestion.getId(),
                                        List.of(correctChoice.getId())
                                ))
                        )
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
        assertEquals(MessageCodes.LEARNING_FINAL_TEST_ATTEMPT_NOT_FOUND, exception.getMessageCode());
    }

    private void mockAllLessonsCompleted() {
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollment.getId()))
                .thenReturn(List.of(completedProgress(quizBlock), completedProgress(textBlock)));
    }

    private LessonBlockProgress completedProgress(LessonBlock block) {
        return LessonBlockProgress.builder()
                .id(UUID.randomUUID())
                .enrollmentId(enrollment.getId())
                .lessonBlockId(block.getId())
                .status(LessonProgressStatus.COMPLETED)
                .build();
    }

    private FinalTestAttempt activeAttempt() {
        return FinalTestAttempt.builder()
                .id(UUID.randomUUID())
                .enrollment(enrollment)
                .finalTest(finalTest)
                .status(FinalTestAttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build();
    }
}
