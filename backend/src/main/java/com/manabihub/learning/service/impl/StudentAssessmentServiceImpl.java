package com.manabihub.learning.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
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
import com.manabihub.learning.dto.internal.InternalQuizQuestionDto;
import com.manabihub.learning.dto.request.FinalTestSubmissionRequest;
import com.manabihub.learning.dto.request.QuizSubmissionRequest;
import com.manabihub.learning.dto.response.FinalTestEligibilityResponse;
import com.manabihub.learning.dto.response.FinalTestStartResponse;
import com.manabihub.learning.dto.response.FinalTestSubmissionResponse;
import com.manabihub.learning.dto.response.QuizSubmissionResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.FinalTestAttempt;
import com.manabihub.learning.entity.LessonBlockProgress;
import com.manabihub.learning.entity.QuizAttempt;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.enums.FinalTestAttemptStatus;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.FinalTestAttemptRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.repository.QuizAttemptRepository;
import com.manabihub.learning.service.StudentAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentAssessmentServiceImpl implements StudentAssessmentService {

    private static final BigDecimal LESSON_QUIZ_PASSING_SCORE = new BigDecimal("80");
    private static final TypeReference<List<InternalQuizQuestionDto>> QUIZ_ITEMS_TYPE = new TypeReference<>() {
    };

    private final CourseRepository courseRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final FinalTestRepository finalTestRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonBlockProgressRepository lessonBlockProgressRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final FinalTestAttemptRepository finalTestAttemptRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public QuizSubmissionResponse submitQuiz(UUID lessonBlockId, QuizSubmissionRequest request) {
        LessonBlock block = lessonBlockRepository.findById(lessonBlockId)
                .filter(value -> !value.isModerationHidden())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.CONTENT_NOT_FOUND,
                        "Quiz lesson block was not found.",
                        HttpStatus.NOT_FOUND
                ));
        if (block.getType() != LessonBlockType.QUIZ) {
            throw new BusinessException(
                    MessageCodes.LEARNING_INVALID_BLOCK_TYPE,
                    "This lesson block is not a quiz.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Enrollment enrollment = lockEnrollment(resolveEnrollment(block.getModule().getCourse().getId()));
        ensureBlockUnlocked(enrollment, block);
        List<InternalQuizQuestionDto> questions = readQuizQuestions(block);
        validateQuizAnswers(request.answers(), questions);

        int correctCount = 0;
        List<QuizSubmissionResponse.QuizQuestionFeedback> feedback = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            InternalQuizQuestionDto question = questions.get(index);
            boolean correct = question.answer().equals(request.answers().get(index));
            if (correct) {
                correctCount++;
            }
            feedback.add(new QuizSubmissionResponse.QuizQuestionFeedback(
                    index,
                    correct,
                    question.answer()
            ));
        }

        BigDecimal score = percentage(correctCount, questions.size());
        boolean passed = score.compareTo(LESSON_QUIZ_PASSING_SCORE) >= 0;
        quizAttemptRepository.save(QuizAttempt.builder()
                .enrollment(enrollment)
                .lessonBlock(block)
                .score(score)
                .passed(passed)
                .answersJson(objectMapper.valueToTree(request.answers()))
                .build());

        LessonBlockProgress progress = lessonBlockProgressRepository
                .findByEnrollmentIdAndLessonBlockId(enrollment.getId(), lessonBlockId)
                .orElseGet(() -> LessonBlockProgress.builder()
                        .enrollmentId(enrollment.getId())
                        .lessonBlockId(lessonBlockId)
                        .status(LessonProgressStatus.NOT_STARTED)
                        .build());
        if (passed) {
            progress.setStatus(LessonProgressStatus.COMPLETED);
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(Instant.now());
            }
        } else if (progress.getStatus() != LessonProgressStatus.COMPLETED) {
            progress.setStatus(LessonProgressStatus.IN_PROGRESS);
        }
        lessonBlockProgressRepository.save(progress);

        return new QuizSubmissionResponse(
                score,
                passed,
                correctCount,
                questions.size(),
                progress.getStatus(),
                feedback
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FinalTestEligibilityResponse getFinalTestEligibility(UUID courseId) {
        Enrollment enrollment = resolveEnrollment(courseId);
        FinalTest finalTest = finalTestRepository.findByCourseId(courseId).orElse(null);
        return evaluateEligibility(enrollment, finalTest);
    }

    @Override
    @Transactional
    public FinalTestStartResponse startFinalTest(UUID courseId) {
        Enrollment enrollment = lockEnrollment(resolveEnrollment(courseId));
        FinalTest finalTest = finalTestRepository.findByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.FINAL_TEST_NOT_FOUND,
                        "Final Test is not configured for this course.",
                        HttpStatus.NOT_FOUND
                ));

        FinalTestEligibilityResponse eligibility = evaluateEligibility(enrollment, finalTest);
        if (eligibility.passed()) {
            throw notEligible("The Final Test has already been passed.");
        }
        if (eligibility.completedLessons() < eligibility.totalLessons()) {
            throw notEligible("Complete every lesson block before starting the Final Test.");
        }

        FinalTestAttempt activeAttempt = finalTestAttemptRepository
                .findFirstByEnrollmentIdAndFinalTestIdAndStatusOrderByStartedAtDesc(
                        enrollment.getId(),
                        finalTest.getId(),
                        FinalTestAttemptStatus.IN_PROGRESS
                )
                .orElse(null);
        if (activeAttempt != null) {
            if (isExpired(activeAttempt)) {
                activeAttempt.setStatus(FinalTestAttemptStatus.TIMED_OUT);
                activeAttempt.setSubmittedAt(Instant.now());
                finalTestAttemptRepository.save(activeAttempt);
            } else {
                return toStartResponse(activeAttempt, eligibility.attemptsAllowed());
            }
        }

        eligibility = evaluateEligibility(enrollment, finalTest);
        if (!eligibility.eligible()) {
            throw notEligible("No Final Test attempts remain.");
        }

        FinalTestAttempt attempt = finalTestAttemptRepository.save(FinalTestAttempt.builder()
                .enrollment(enrollment)
                .finalTest(finalTest)
                .status(FinalTestAttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build());
        return toStartResponse(attempt, eligibility.attemptsAllowed());
    }

    @Override
    @Transactional
    public FinalTestSubmissionResponse submitFinalTest(
            UUID courseId,
            FinalTestSubmissionRequest request
    ) {
        Enrollment enrollment = lockEnrollment(resolveEnrollment(courseId));
        FinalTestAttempt attempt = finalTestAttemptRepository
                .findOwnedByIdForUpdate(request.attemptId(), enrollment.getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_FINAL_TEST_ATTEMPT_NOT_FOUND,
                        "Final Test attempt was not found.",
                        HttpStatus.NOT_FOUND
                ));
        if (!attempt.getFinalTest().getCourse().getId().equals(courseId)) {
            throw new BusinessException(
                    MessageCodes.LEARNING_FINAL_TEST_ATTEMPT_NOT_FOUND,
                    "Final Test attempt was not found.",
                    HttpStatus.NOT_FOUND
            );
        }
        if (attempt.getStatus() != FinalTestAttemptStatus.IN_PROGRESS) {
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "This Final Test attempt has already ended.",
                    HttpStatus.CONFLICT
            );
        }
        if (isExpired(attempt)) {
            attempt.setStatus(FinalTestAttemptStatus.TIMED_OUT);
            attempt.setSubmittedAt(Instant.now());
            finalTestAttemptRepository.save(attempt);
            throw new BusinessException(
                    MessageCodes.LEARNING_FINAL_TEST_ATTEMPT_EXPIRED,
                    "The Final Test time limit has expired.",
                    HttpStatus.BAD_REQUEST
            );
        }

        FinalTest finalTest = attempt.getFinalTest();
        Map<UUID, List<UUID>> submittedAnswers = validateFinalTestAnswers(
                request.answers(),
                finalTest.getQuestions()
        );
        int correctCount = 0;
        List<FinalTestSubmissionResponse.FinalTestQuestionFeedback> feedback = new ArrayList<>();
        for (FinalTestQuestion question : finalTest.getQuestions()) {
            List<UUID> selected = submittedAnswers.get(question.getId());
            List<UUID> correctChoices = question.getChoices().stream()
                    .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                    .map(FinalTestChoice::getId)
                    .toList();
            boolean correct = selected.size() == correctChoices.size()
                    && new HashSet<>(selected).containsAll(correctChoices);
            if (correct) {
                correctCount++;
            }
            feedback.add(new FinalTestSubmissionResponse.FinalTestQuestionFeedback(
                    question.getId(),
                    correct,
                    question.getExplanation(),
                    correctChoices
            ));
        }

        BigDecimal score = percentage(correctCount, finalTest.getQuestions().size());
        boolean passed = score.compareTo(BigDecimal.valueOf(finalTest.getPassingScore())) >= 0;
        attempt.setScore(score);
        attempt.setPassed(passed);
        attempt.setAnswersJson(objectMapper.valueToTree(request.answers()));
        attempt.setStatus(FinalTestAttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        finalTestAttemptRepository.save(attempt);

        if (passed) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            if (enrollment.getCompletedAt() == null) {
                enrollment.setCompletedAt(Instant.now());
            }
            enrollmentRepository.save(enrollment);
        }

        return new FinalTestSubmissionResponse(
                attempt.getId(),
                score,
                passed,
                !passed,
                correctCount,
                finalTest.getQuestions().size(),
                feedback
        );
    }

    @Override
    @Transactional
    public void terminateFinalTestAttempt(UUID courseId, UUID attemptId) {
        Enrollment enrollment = lockEnrollment(resolveEnrollment(courseId));
        FinalTestAttempt attempt = finalTestAttemptRepository
                .findOwnedByIdForUpdate(attemptId, enrollment.getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_FINAL_TEST_ATTEMPT_NOT_FOUND,
                        "Final Test attempt was not found.",
                        HttpStatus.NOT_FOUND
                ));
        if (!attempt.getFinalTest().getCourse().getId().equals(courseId)) {
            throw new BusinessException(
                    MessageCodes.LEARNING_FINAL_TEST_ATTEMPT_NOT_FOUND,
                    "Final Test attempt was not found.",
                    HttpStatus.NOT_FOUND
            );
        }
        if (attempt.getStatus() == FinalTestAttemptStatus.TERMINATED_FOR_VIOLATION) {
            return;
        }
        if (attempt.getStatus() != FinalTestAttemptStatus.IN_PROGRESS) {
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "This Final Test attempt has already ended.",
                    HttpStatus.CONFLICT
            );
        }
        attempt.setStatus(FinalTestAttemptStatus.TERMINATED_FOR_VIOLATION);
        attempt.setPassed(false);
        attempt.setSubmittedAt(Instant.now());
        finalTestAttemptRepository.save(attempt);
    }

    private Enrollment resolveEnrollment(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COURSE_NOT_FOUND,
                        "Course was not found.",
                        HttpStatus.NOT_FOUND
                ));
        StudentProfile student = studentProfileRepository
                .findByUser_Id(currentUserService.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_NOT_ENROLLED,
                        "You are not enrolled in this course.",
                        HttpStatus.FORBIDDEN
                ));
        return enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId())
                .filter(value -> value.getStatus() == EnrollmentStatus.ACTIVE
                        || value.getStatus() == EnrollmentStatus.COMPLETED)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_NOT_ENROLLED,
                        "You are not enrolled in this course.",
                        HttpStatus.FORBIDDEN
                ));
    }

    private Enrollment lockEnrollment(Enrollment enrollment) {
        return enrollmentRepository.findByIdForUpdate(enrollment.getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_NOT_ENROLLED,
                        "Enrollment was not found.",
                        HttpStatus.FORBIDDEN
                ));
    }

    private void ensureBlockUnlocked(Enrollment enrollment, LessonBlock block) {
        List<LessonBlock> allBlocks = enrollment.getCourse().getModules().stream()
                .sorted(Comparator.comparingInt(module -> module.getOrderIndex()))
                .flatMap(module -> module.getBlocks().stream()
                        .filter(value -> !value.isModerationHidden())
                        .sorted(Comparator.comparingInt(LessonBlock::getOrderIndex)))
                .toList();
        Map<UUID, LessonBlockProgress> progressByBlockId = lessonBlockProgressRepository
                .findByEnrollmentId(enrollment.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        LessonBlockProgress::getLessonBlockId,
                        value -> value
                ));
        int blockIndex = -1;
        for (int index = 0; index < allBlocks.size(); index++) {
            if (allBlocks.get(index).getId().equals(block.getId())) {
                blockIndex = index;
                break;
            }
        }
        if (blockIndex < 0) {
            throw new BusinessException(MessageCodes.CONTENT_NOT_FOUND, "Lesson block was not found.", HttpStatus.NOT_FOUND);
        }
        boolean previousIncomplete = allBlocks.subList(0, blockIndex).stream()
                .anyMatch(previous -> {
                    LessonBlockProgress progress = progressByBlockId.get(previous.getId());
                    return progress == null || progress.getStatus() != LessonProgressStatus.COMPLETED;
                });
        LessonBlockProgress current = progressByBlockId.get(block.getId());
        if (previousIncomplete && (current == null || current.getStatus() != LessonProgressStatus.COMPLETED)) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Complete the previous lesson before opening this lesson.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private List<InternalQuizQuestionDto> readQuizQuestions(LessonBlock block) {
        if (StringUtils.hasText(block.getQuizItemsJson())) {
            try {
                List<InternalQuizQuestionDto> questions =
                        objectMapper.readValue(block.getQuizItemsJson(), QUIZ_ITEMS_TYPE);
                if (!questions.isEmpty()) {
                    return questions;
                }
            } catch (JsonProcessingException exception) {
                throw invalidAnswers("Quiz configuration is invalid.");
            }
        }
        if (StringUtils.hasText(block.getQuizQuestion())
                && StringUtils.hasText(block.getQuizAnswer())
                && StringUtils.hasText(block.getQuizOptionsJson())) {
            try {
                List<String> options = objectMapper.readValue(
                        block.getQuizOptionsJson(),
                        new TypeReference<>() {
                        }
                );
                return List.of(new InternalQuizQuestionDto(
                        block.getQuizQuestion(),
                        options,
                        block.getQuizAnswer()
                ));
            } catch (JsonProcessingException exception) {
                throw invalidAnswers("Quiz configuration is invalid.");
            }
        }
        throw invalidAnswers("Quiz has no questions.");
    }

    private void validateQuizAnswers(
            List<String> answers,
            List<InternalQuizQuestionDto> questions
    ) {
        if (answers == null || answers.size() != questions.size()) {
            throw invalidAnswers("Answer every quiz question before submitting.");
        }
        for (int index = 0; index < questions.size(); index++) {
            InternalQuizQuestionDto question = questions.get(index);
            String answer = answers.get(index);
            if (!StringUtils.hasText(answer)
                    || question.options() == null
                    || !question.options().contains(answer)
                    || !StringUtils.hasText(question.answer())) {
                throw invalidAnswers("Quiz answers contain an invalid option.");
            }
        }
    }

    private Map<UUID, List<UUID>> validateFinalTestAnswers(
            List<FinalTestSubmissionRequest.FinalTestAnswer> answers,
            List<FinalTestQuestion> questions
    ) {
        if (answers == null || answers.size() != questions.size()) {
            throw invalidAnswers("Answer every Final Test question before submitting.");
        }
        Map<UUID, List<UUID>> values = new HashMap<>();
        for (FinalTestSubmissionRequest.FinalTestAnswer answer : answers) {
            if (values.putIfAbsent(answer.questionId(), answer.selectedChoiceIds()) != null) {
                throw invalidAnswers("A Final Test question was answered more than once.");
            }
        }
        for (FinalTestQuestion question : questions) {
            List<UUID> selected = values.get(question.getId());
            if (selected == null || selected.isEmpty() || new HashSet<>(selected).size() != selected.size()) {
                throw invalidAnswers("Answer every Final Test question with valid choices.");
            }
            Set<UUID> allowed = question.getChoices().stream()
                    .map(FinalTestChoice::getId)
                    .collect(java.util.stream.Collectors.toSet());
            if (!allowed.containsAll(selected)) {
                throw invalidAnswers("Final Test answers contain an invalid choice.");
            }
        }
        return values;
    }

    private FinalTestEligibilityResponse evaluateEligibility(
            Enrollment enrollment,
            FinalTest finalTest
    ) {
        List<LessonBlock> blocks = enrollment.getCourse().getModules().stream()
                .flatMap(module -> module.getBlocks().stream())
                .filter(block -> !block.isModerationHidden())
                .toList();
        Set<UUID> completedBlockIds = lessonBlockProgressRepository
                .findByEnrollmentId(enrollment.getId())
                .stream()
                .filter(progress -> progress.getStatus() == LessonProgressStatus.COMPLETED)
                .map(LessonBlockProgress::getLessonBlockId)
                .collect(java.util.stream.Collectors.toSet());
        int completedLessons = (int) blocks.stream()
                .filter(block -> completedBlockIds.contains(block.getId()))
                .count();

        if (finalTest == null || finalTest.getQuestions().isEmpty()) {
            return new FinalTestEligibilityResponse(
                    false,
                    false,
                    "FINAL_TEST_NOT_CONFIGURED",
                    null,
                    blocks.size(),
                    completedLessons,
                    0,
                    0,
                    false
            );
        }

        int attemptsAllowed = finalTest.getMaxRetakes() + 1;
        int attemptsUsed = Math.toIntExact(finalTestAttemptRepository
                .countByEnrollmentIdAndFinalTestId(enrollment.getId(), finalTest.getId()));
        boolean passed = finalTestAttemptRepository
                .existsByEnrollmentIdAndFinalTestIdAndPassedTrue(enrollment.getId(), finalTest.getId());
        String reason = null;
        if (passed) {
            reason = "FINAL_TEST_ALREADY_PASSED";
        } else if (blocks.isEmpty() || completedLessons != blocks.size()) {
            reason = "LESSONS_INCOMPLETE";
        } else if (attemptsUsed >= attemptsAllowed) {
            reason = "ATTEMPTS_EXHAUSTED";
        }

        return new FinalTestEligibilityResponse(
                true,
                reason == null,
                reason,
                finalTest.getId(),
                blocks.size(),
                completedLessons,
                attemptsUsed,
                attemptsAllowed,
                passed
        );
    }

    private FinalTestStartResponse toStartResponse(
            FinalTestAttempt attempt,
            int attemptsAllowed
    ) {
        FinalTest finalTest = attempt.getFinalTest();
        int attemptsUsed = Math.toIntExact(finalTestAttemptRepository
                .countByEnrollmentIdAndFinalTestId(
                        attempt.getEnrollment().getId(),
                        finalTest.getId()
                ));
        List<FinalTestStartResponse.FinalTestQuestionView> questions = finalTest.getQuestions()
                .stream()
                .map(question -> new FinalTestStartResponse.FinalTestQuestionView(
                        question.getId(),
                        question.getContent(),
                        question.getChoices().stream()
                                .map(choice -> new FinalTestStartResponse.FinalTestChoiceView(
                                        choice.getId(),
                                        choice.getContent()
                                ))
                                .toList()
                ))
                .toList();
        return new FinalTestStartResponse(
                attempt.getId(),
                finalTest.getTimeLimitMinutes(),
                finalTest.getPassingScore(),
                Math.max(0, attemptsAllowed - attemptsUsed),
                attempt.getStartedAt(),
                attempt.getStartedAt().plusSeconds(finalTest.getTimeLimitMinutes() * 60L),
                questions
        );
    }

    private boolean isExpired(FinalTestAttempt attempt) {
        return Instant.now().isAfter(
                attempt.getStartedAt().plusSeconds(attempt.getFinalTest().getTimeLimitMinutes() * 60L)
        );
    }

    private BigDecimal percentage(int correctCount, int totalQuestions) {
        if (totalQuestions <= 0) {
            throw invalidAnswers("Assessment has no questions.");
        }
        return BigDecimal.valueOf(correctCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);
    }

    private BusinessException invalidAnswers(String message) {
        return new BusinessException(
                MessageCodes.LEARNING_INVALID_QUIZ_ANSWERS,
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException notEligible(String message) {
        return new BusinessException(
                MessageCodes.LEARNING_FINAL_TEST_NOT_ELIGIBLE,
                message,
                HttpStatus.FORBIDDEN
        );
    }
}
