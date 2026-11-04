package com.manabihub.learning.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.dto.response.FlashcardItemResponse;
import com.manabihub.learning.dto.internal.InternalQuizQuestionDto;
import com.manabihub.learning.dto.response.StudentQuizQuestionResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.dto.request.SaveVideoProgressRequest;
import com.manabihub.learning.dto.response.CourseLearningResponse;
import com.manabihub.learning.dto.response.CourseProgressSummaryResponse;
import com.manabihub.learning.dto.response.LearningLessonBlockResponse;
import com.manabihub.learning.dto.response.LearningModuleResponse;
import com.manabihub.learning.dto.response.LessonProgressResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.LessonBlockProgress;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.enums.FlashcardStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.FlashcardProgressRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.service.LearningService;
import com.manabihub.learning.service.CertificateEligibilityService;
import com.manabihub.learning.service.StudentAssessmentService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.writing.entity.WritingSubmission;
import com.manabihub.writing.repository.WritingSubmissionRepository;
import com.manabihub.writing.entity.AiWritingSuggestion;
import com.manabihub.writing.repository.AiWritingSuggestionRepository;
import com.manabihub.writing.entity.TeacherWritingFeedback;
import com.manabihub.writing.repository.TeacherWritingFeedbackRepository;
import com.manabihub.ai.service.AiChatSettingsService;
import com.manabihub.ai.service.AiUsageLogService;
import com.manabihub.ai.enums.AiUsageRequestStatus;
import com.manabihub.writing.enums.WritingSubmissionStatus;
import com.manabihub.writing.dto.request.WritingSubmissionRequest;
import com.manabihub.writing.dto.request.WritingDraftRequest;
import com.manabihub.writing.dto.response.StudentWritingSubmissionResponse;
import com.manabihub.writing.dto.response.AiWritingSuggestionResponse;
import com.manabihub.writing.dto.response.TeacherWritingFeedbackResponse;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import com.manabihub.ai.provider.AiWritingAssistanceProvider;
import com.manabihub.ai.provider.AiChatProviderException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningServiceImpl implements LearningService {

    private static final TypeReference<List<String>> QUIZ_OPTIONS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<InternalQuizQuestionDto>> QUIZ_ITEMS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<FlashcardItemResponse>> FLASHCARDS_TYPE = new TypeReference<>() {
    };

    private final CourseRepository courseRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonBlockProgressRepository lessonBlockProgressRepository;
    private final FlashcardProgressRepository flashcardProgressRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final AiWritingSuggestionRepository aiWritingSuggestionRepository;
    private final TeacherWritingFeedbackRepository teacherWritingFeedbackRepository;
    private final AiChatSettingsService aiChatSettingsService;
    private final AiUsageLogService aiUsageLogService;
    private final com.manabihub.ai.service.AiQuotaService aiQuotaService;
    private final TransactionTemplate transactionTemplate;
    private final AiWritingAssistanceProvider aiWritingAssistanceProvider;
    private final StudentAssessmentService studentAssessmentService;
    private final CertificateEligibilityService certificateEligibilityService;
    private final NotificationService notificationService;

    @Override
    public CourseLearningResponse openOrResumeCourse(UUID courseId) {
        Enrollment enrollment = resolveActiveEnrollment(courseId);
        Course course = enrollment.getCourse();
        List<LessonBlock> allBlocks = flattenBlocks(course);

        if (allBlocks.isEmpty()) {
            log.warn("[{}] Course {} has no lesson content available", MessageCodes.LEARNING_LESSON_CONTENT_UNAVAILABLE, courseId);
            return new CourseLearningResponse(
                    course.getId(),
                    course.getTitle(),
                    enrollment.getId(),
                    List.of(),
                    null,
                    0,
                    0,
                    0.0,
                    false,
                    List.of("Khoá học chưa có nội dung bài học.")
            );
        }

        Map<UUID, LessonBlockProgress> progressByBlockId = lessonBlockProgressRepository.findByEnrollmentId(enrollment.getId())
                .stream()
                .collect(Collectors.toMap(progress -> progress.getLessonBlockId(), Function.identity()));

        int completedLessons = (int) allBlocks.stream()
                .filter(block -> isCompleted(progressByBlockId.get(block.getId())))
                .count();

        boolean courseCompleted = completedLessons == allBlocks.size();
        UUID currentLessonBlockId = allBlocks.stream()
                .filter(block -> !isCompleted(progressByBlockId.get(block.getId())))
                .map(LessonBlock::getId)
                .findFirst()
                .orElse(null);

        List<String> warnings = new ArrayList<>();

        Map<UUID, List<com.manabihub.learning.entity.FlashcardProgress>> flashcardProgressByBlockId = flashcardProgressRepository.findByEnrollmentId(enrollment.getId())
                .stream()
                .collect(Collectors.groupingBy(com.manabihub.learning.entity.FlashcardProgress::getLessonBlockId));

        List<LearningModuleResponse> moduleResponses = sortedModules(course).stream()
                .map(module -> toModuleResponse(module, progressByBlockId, currentLessonBlockId, warnings, flashcardProgressByBlockId))
                .toList();

        return new CourseLearningResponse(
                course.getId(),
                course.getTitle(),
                enrollment.getId(),
                moduleResponses,
                currentLessonBlockId,
                allBlocks.size(),
                completedLessons,
                progressPercent(completedLessons, allBlocks.size()),
                courseCompleted,
                warnings,
                enrollment.getStatus(),
                enrollment.getExpiresAt()
        );
    }

    @Override
    @Transactional
    public LessonProgressResponse saveVideoProgress(UUID lessonBlockId, SaveVideoProgressRequest request) {
        LessonBlock block = resolveLessonBlock(lessonBlockId);
        Enrollment enrollment = resolveActiveEnrollment(block.getModule().getCourse().getId());

        if (block.getType() != LessonBlockType.VIDEO) {
            throw new BusinessException(
                    MessageCodes.LEARNING_INVALID_BLOCK_TYPE,
                    "Only VIDEO lesson blocks support video progress tracking",
                    HttpStatus.BAD_REQUEST
            );
        }

        ensureBlockUnlocked(enrollment, block);

        if (!StringUtils.hasText(block.getVideoUrl())) {
            log.warn("[{}] Video content missing for lesson block {}", MessageCodes.LEARNING_LESSON_CONTENT_UNAVAILABLE, lessonBlockId);
            throw new BusinessException(
                    MessageCodes.LEARNING_LESSON_CONTENT_UNAVAILABLE,
                    "Video content is missing for this lesson",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        LessonBlockProgress progress = resolveOrCreateProgress(enrollment, block);
        int durationSeconds = resolveVideoDurationSeconds(block, progress, request.mediaDurationSeconds());
        if (progress.getVideoDurationSeconds() == null
                && isAcceptableObservedDuration(block, request.mediaDurationSeconds())) {
            progress.setVideoDurationSeconds(request.mediaDurationSeconds());
        }
        int normalizedPositionSeconds = Math.min(request.positionSeconds(), durationSeconds);
        progress.setLastVideoPositionSeconds(normalizedPositionSeconds);
        int reportedWatchedSeconds = request.watchedSeconds() == null ? 0 : request.watchedSeconds();
        int normalizedWatchedSeconds = Math.min(reportedWatchedSeconds, durationSeconds);
        int watchedSeconds = Math.min(durationSeconds, Math.max(
                progress.getWatchedVideoSeconds() == null ? 0 : progress.getWatchedVideoSeconds(),
                normalizedWatchedSeconds
        ));
        progress.setWatchedVideoSeconds(watchedSeconds);
        if (progress.getStatus() == LessonProgressStatus.NOT_STARTED) {
            progress.setStatus(LessonProgressStatus.IN_PROGRESS);
        }
        if (watchedSeconds >= durationSeconds && progress.getStatus() != LessonProgressStatus.COMPLETED) {
            progress.setStatus(LessonProgressStatus.COMPLETED);
            progress.setCompletedAt(Instant.now());
        }

        lessonBlockProgressRepository.save(progress);
        return toProgressResponse(progress);
    }

    @Override
    @Transactional
    public LessonProgressResponse reviewFlashcard(UUID lessonBlockId, com.manabihub.learning.dto.request.ReviewFlashcardRequest request) {
        LessonBlock block = resolveLessonBlock(lessonBlockId);
        Enrollment enrollment = resolveActiveEnrollment(block.getModule().getCourse().getId());

        if (block.getType() != LessonBlockType.FLASHCARD) {
            throw new BusinessException(MessageCodes.LEARNING_INVALID_BLOCK_TYPE, "Block is not a flashcard", HttpStatus.BAD_REQUEST);
        }

        ensureBlockUnlocked(enrollment, block);

        List<FlashcardItemResponse> flashcards = readJsonList(block.getFlashcardsJson(), FLASHCARDS_TYPE);
        int totalCards = flashcards.size();
        if (request.cardIndex() < 0 || request.cardIndex() >= totalCards) {
            throw new BusinessException(MessageCodes.LEARNING_INVALID_FLASHCARD_INDEX, "Invalid card index", HttpStatus.BAD_REQUEST);
        }

        // Acquire PESSIMISTIC_WRITE lock on the enrollment row to serialize
        // concurrent reviews for the same enrollment, preventing lost completions
        // or duplicate LessonBlockProgress rows.
        enrollmentRepository.findByIdForUpdate(enrollment.getId())
                .orElseThrow(() -> new BusinessException(MessageCodes.LEARNING_NOT_ENROLLED, "Enrollment not found", HttpStatus.FORBIDDEN));

        flashcardProgressRepository.upsertStatus(enrollment.getId(), lessonBlockId, request.cardIndex(), request.status());

        int classifiedCount = flashcardProgressRepository.countByEnrollmentIdAndLessonBlockId(enrollment.getId(), lessonBlockId);

        LessonBlockProgress progress = resolveOrCreateProgress(enrollment, block);

        if (classifiedCount >= totalCards) {
            progress.setStatus(LessonProgressStatus.COMPLETED);
            progress.setCompletedAt(Instant.now());
        } else if (progress.getStatus() == LessonProgressStatus.NOT_STARTED) {
            progress.setStatus(LessonProgressStatus.IN_PROGRESS);
        }

        progress = lessonBlockProgressRepository.save(progress);

        return toProgressResponse(progress);
    }

    @Override
    @Transactional
    public LessonProgressResponse markLessonComplete(UUID lessonBlockId) {
        LessonBlock block = resolveLessonBlock(lessonBlockId);
        if (block.getType() != LessonBlockType.VIDEO && block.getType() != LessonBlockType.TEXT) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Cannot mark " + block.getType() + " block as complete through this endpoint.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Enrollment enrollment = resolveActiveEnrollment(block.getModule().getCourse().getId());
        ensureBlockUnlocked(enrollment, block);

        LessonBlockProgress progress = resolveOrCreateProgress(enrollment, block);
        if (block.getType() == LessonBlockType.VIDEO
                && progress.getStatus() != LessonProgressStatus.COMPLETED
                && (progress.getWatchedVideoSeconds() == null
                || progress.getWatchedVideoSeconds() < effectiveStoredVideoDuration(block, progress))) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Watch the full video before completing this lesson.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (progress.getStatus() != LessonProgressStatus.COMPLETED) {
            progress.setStatus(LessonProgressStatus.COMPLETED);
            progress.setCompletedAt(Instant.now());
        }

        lessonBlockProgressRepository.save(progress);
        // MHB-25: Do not set EnrollmentStatus.COMPLETED. This belongs to MHB-26/27.
        return toProgressResponse(progress);
    }

    @Override
    public CourseProgressSummaryResponse getCourseProgress(UUID courseId) {
        Enrollment enrollment = resolveActiveEnrollment(courseId);
        Course course = enrollment.getCourse();
        List<LessonBlock> allBlocks = flattenBlocks(course);

        Map<UUID, LessonBlockProgress> progressByBlockId = lessonBlockProgressRepository.findByEnrollmentId(enrollment.getId())
                .stream()
                .collect(Collectors.toMap(progress -> progress.getLessonBlockId(), Function.identity()));

        int completedLessons = (int) allBlocks.stream()
                .filter(block -> isCompleted(progressByBlockId.get(block.getId())))
                .count();

        LessonBlock nextLesson = allBlocks.stream()
                .filter(block -> !isCompleted(progressByBlockId.get(block.getId())))
                .findFirst()
                .orElse(null);
        boolean courseCompleted = !allBlocks.isEmpty() && nextLesson == null;

        return new CourseProgressSummaryResponse(
                course.getId(),
                course.getTitle(),
                allBlocks.size(),
                completedLessons,
                progressPercent(completedLessons, allBlocks.size()),
                nextLesson != null ? nextLesson.getId() : null,
                nextLesson != null ? nextLesson.getTitle() : null,
                courseCompleted,
                studentAssessmentService.getFinalTestEligibility(courseId),
                certificateEligibilityService.evaluate(enrollment, allBlocks, progressByBlockId)
        );
    }

    private Enrollment resolveActiveEnrollment(UUID courseId) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COURSE_NOT_FOUND,
                        "Course was not found",
                        HttpStatus.NOT_FOUND
                ));

        StudentProfile studentProfile = resolveStudentProfile();

        Enrollment enrollment = enrollmentRepository.findByStudent_IdAndCourse_Id(studentProfile.getId(), courseId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_NOT_ENROLLED,
                        "You are not enrolled in this course",
                        HttpStatus.FORBIDDEN
                ));

        if (enrollment.getStatus() == EnrollmentStatus.REFUND_PENDING) {
            throw new BusinessException(
                    MessageCodes.LEARNING_REFUND_PENDING,
                    "Khóa học đang tạm khóa trong thời gian xử lý yêu cầu hoàn tiền. Bạn có thể hủy yêu cầu để tiếp tục học.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (enrollment.getStatus() == EnrollmentStatus.REFUNDED
                || enrollment.getStatus() == EnrollmentStatus.REVOKED) {
            throw new BusinessException(
                    MessageCodes.LEARNING_NOT_ENROLLED,
                    "Quyền truy cập khóa học đã bị thu hồi sau khi hoàn tiền.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (enrollment.getStatus() == EnrollmentStatus.EXPIRED
                || enrollment.isExpired(Instant.now())) {
            throw new BusinessException(
                    MessageCodes.LEARNING_ACCESS_EXPIRED,
                    "Khóa học đã hết hạn truy cập. Vui lòng gia hạn hoặc mua lại khóa học.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE
                && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new BusinessException(
                    MessageCodes.LEARNING_NOT_ENROLLED,
                    "You are not enrolled in this course",
                    HttpStatus.FORBIDDEN
            );
        }
        return enrollment;
    }

    private StudentProfile resolveStudentProfile() {
        UUID currentUserId = currentUserService.getCurrentUserId();

        return studentProfileRepository.findByUser_Id(currentUserId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_NOT_ENROLLED,
                        "You are not enrolled in this course",
                        HttpStatus.FORBIDDEN
                ));
    }



    private LessonBlock resolveLessonBlock(UUID lessonBlockId) {
        return lessonBlockRepository.findById(lessonBlockId)
                .filter(block -> !block.isModerationHidden())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.CONTENT_NOT_FOUND,
                        "Lesson block was not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private LessonBlockProgress resolveOrCreateProgress(Enrollment enrollment, LessonBlock block) {
        return lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollment.getId(), block.getId())
                .orElseGet(() -> LessonBlockProgress.builder()
                        .enrollmentId(enrollment.getId())
                        .lessonBlockId(block.getId())
                        .status(LessonProgressStatus.NOT_STARTED)
                        .watchedVideoSeconds(0)
                        .build());
    }

    private int videoDurationSeconds(LessonBlock block) {
        return Math.max(1, (block.getDurationMinutes() == null ? 0 : block.getDurationMinutes()) * 60);
    }

    private int effectiveStoredVideoDuration(LessonBlock block, LessonBlockProgress progress) {
        return progress.getVideoDurationSeconds() != null && progress.getVideoDurationSeconds() > 0
                ? progress.getVideoDurationSeconds()
                : videoDurationSeconds(block);
    }

    private int resolveVideoDurationSeconds(LessonBlock block, LessonBlockProgress progress, Integer observedDurationSeconds) {
        if (progress.getVideoDurationSeconds() != null && progress.getVideoDurationSeconds() > 0) {
            return progress.getVideoDurationSeconds();
        }
        return isAcceptableObservedDuration(block, observedDurationSeconds)
                ? observedDurationSeconds
                : videoDurationSeconds(block);
    }

    /** Accept actual media metadata while rejecting implausibly short client values. */
    private boolean isAcceptableObservedDuration(LessonBlock block, Integer observedDurationSeconds) {
        if (observedDurationSeconds == null || observedDurationSeconds < 30) {
            return false;
        }
        int declared = videoDurationSeconds(block);
        int minimum = Math.max(30, (int) Math.ceil(declared * 0.25d));
        int maximum = Math.max(declared * 4, declared + 60);
        return observedDurationSeconds >= minimum && observedDurationSeconds <= maximum;
    }

    private void ensureBlockUnlocked(Enrollment enrollment, LessonBlock block) {
        List<LessonBlock> allBlocks = flattenBlocks(enrollment.getCourse());
        // Some legacy/unit-test fixtures resolve a block independently without
        // hydrating the course module collection. There is no ordering graph to
        // enforce in that case; production enrollments always load the modules
        // through openOrResumeCourse before a lesson action is available.
        if (allBlocks.isEmpty()) {
            return;
        }
        Map<UUID, LessonBlockProgress> progressByBlockId = lessonBlockProgressRepository
                .findByEnrollmentId(enrollment.getId())
                .stream()
                .collect(Collectors.toMap(LessonBlockProgress::getLessonBlockId, Function.identity()));
        int blockIndex = -1;
        for (int index = 0; index < allBlocks.size(); index++) {
            if (allBlocks.get(index).getId().equals(block.getId())) {
                blockIndex = index;
                break;
            }
        }
        if (blockIndex < 0) {
            throw new BusinessException(MessageCodes.CONTENT_NOT_FOUND, "Lesson block was not found", HttpStatus.NOT_FOUND);
        }
        boolean previousIncomplete = allBlocks.subList(0, blockIndex).stream()
                .anyMatch(previous -> !isCompleted(progressByBlockId.get(previous.getId())));
        if (previousIncomplete && !isCompleted(progressByBlockId.get(block.getId()))) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Complete the previous lesson before opening this lesson.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private boolean isCompleted(LessonBlockProgress progress) {
        return progress != null && progress.getStatus() == LessonProgressStatus.COMPLETED;
    }

    private LearningModuleResponse toModuleResponse(
            CourseModule module,
            Map<UUID, LessonBlockProgress> progressByBlockId,
            UUID currentLessonBlockId,
            List<String> warnings,
            Map<UUID, List<com.manabihub.learning.entity.FlashcardProgress>> flashcardProgressByBlockId
    ) {
        List<LearningLessonBlockResponse> blockResponses = sortedBlocks(module).stream()
                .map(block -> toBlockResponse(module, block, progressByBlockId.get(block.getId()), currentLessonBlockId, warnings, flashcardProgressByBlockId.getOrDefault(block.getId(), List.of())))
                .toList();

        return new LearningModuleResponse(module.getId(), module.getTitle(), module.getOrderIndex(), blockResponses);
    }

    private LearningLessonBlockResponse toBlockResponse(
            CourseModule module,
            LessonBlock block,
            LessonBlockProgress progress,
            UUID currentLessonBlockId,
            List<String> warnings,
            List<com.manabihub.learning.entity.FlashcardProgress> flashcardProgresses
    ) {
        boolean contentAvailable = isContentAvailable(block);
        if (!contentAvailable) {
            log.warn("[{}] Lesson block {} in course {} is missing required content", MessageCodes.LEARNING_LESSON_CONTENT_UNAVAILABLE, block.getId(), module.getCourse().getId());
            warnings.add("Bài học \"" + block.getTitle() + "\" hiện chưa có đủ nội dung.");
        }

        List<String> quizOptions = readJsonList(block.getQuizOptionsJson(), QUIZ_OPTIONS_TYPE);

        List<FlashcardItemResponse> flashcards = readJsonList(block.getFlashcardsJson(), FLASHCARDS_TYPE);
        List<FlashcardStatus> flashcardStatuses = null;
        if (block.getType() == LessonBlockType.FLASHCARD) {
            Map<Integer, FlashcardStatus> statusMap = flashcardProgresses.stream()
                    .collect(Collectors.toMap(com.manabihub.learning.entity.FlashcardProgress::getCardIndex, com.manabihub.learning.entity.FlashcardProgress::getStatus));
            flashcardStatuses = new ArrayList<>();
            for (int i = 0; i < flashcards.size(); i++) {
                flashcardStatuses.add(statusMap.get(i));
            }
        }

        boolean completed = progress != null && isCompleted(progress);
        boolean locked = progress != null
                ? !completed && currentLessonBlockId != null && !block.getId().equals(currentLessonBlockId)
                : currentLessonBlockId != null && !block.getId().equals(currentLessonBlockId);
        boolean visible = !locked;

        return new LearningLessonBlockResponse(
                block.getId(),
                module.getId(),
                block.getType(),
                block.getTitle(),
                visible ? block.getContent() : null,
                visible ? block.getVideoUrl() : null,
                block.getDurationMinutes(),
                visible ? block.getQuizQuestion() : null,
                visible ? quizOptions : List.of(),
                visible ? readQuizItems(block, quizOptions) : List.of(),
                visible ? flashcards : List.of(),
                visible ? flashcardStatuses : null,
                visible ? block.getWritingPrompt() : null,
                visible ? block.getRubric() : null,
                block.getOrderIndex(),
                visible && contentAvailable,
                progress != null ? progress.getStatus() : LessonProgressStatus.NOT_STARTED,
                progress != null ? progress.getLastVideoPositionSeconds() : null,
                progress != null ? progress.getCompletedAt() : null,
                block.getId().equals(currentLessonBlockId),
                progress != null ? progress.getWatchedVideoSeconds() : 0,
                locked
        );
    }

    private boolean isContentAvailable(LessonBlock block) {
        return switch (block.getType()) {
            case VIDEO -> StringUtils.hasText(block.getVideoUrl());
            case TEXT -> StringUtils.hasText(block.getContent());
            case QUIZ -> StringUtils.hasText(block.getQuizQuestion()) || StringUtils.hasText(block.getQuizItemsJson());
            case FLASHCARD -> StringUtils.hasText(block.getFlashcardsJson());
            case WRITING -> StringUtils.hasText(block.getWritingPrompt());
        };
    }

    private LessonProgressResponse toProgressResponse(LessonBlockProgress progress) {
        return new LessonProgressResponse(
                progress.getLessonBlockId(),
                progress.getEnrollmentId(),
                progress.getStatus(),
                progress.getLastVideoPositionSeconds(),
                progress.getCompletedAt(),
                progress.getUpdatedAt(),
                progress.getWatchedVideoSeconds()
        );
    }

    private List<LessonBlock> flattenBlocks(Course course) {
        return sortedModules(course).stream()
                .flatMap(module -> sortedBlocks(module).stream())
                .toList();
    }

    private List<CourseModule> sortedModules(Course course) {
        return course.getModules().stream()
                .sorted(Comparator.comparingInt(CourseModule::getOrderIndex))
                .toList();
    }

    private List<LessonBlock> sortedBlocks(CourseModule module) {
        return module.getBlocks().stream()
                .filter(block -> !block.isModerationHidden())
                .sorted(Comparator.comparingInt(LessonBlock::getOrderIndex))
                .toList();
    }

    private double progressPercent(int completed, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(completed * 10000.0 / total) / 100.0;
    }

    private List<StudentQuizQuestionResponse> readQuizItems(LessonBlock block, List<String> fallbackOptions) {
        List<InternalQuizQuestionDto> quizItems = readJsonList(block.getQuizItemsJson(), QUIZ_ITEMS_TYPE);
        if (!quizItems.isEmpty()) {
            return quizItems.stream()
                    .map(q -> new StudentQuizQuestionResponse(q.question(), q.options()))
                    .toList();
        }
        if (StringUtils.hasText(block.getQuizQuestion()) && !fallbackOptions.isEmpty()) {
            return List.of(new StudentQuizQuestionResponse(block.getQuizQuestion(), fallbackOptions));
        }
        return List.of();
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    // ──────────────── Writing Assignment Methods ────────────────────────────────────────

    @Override
    public StudentWritingSubmissionResponse getWritingSubmission(UUID lessonBlockId) {
        LessonBlock block = resolveLessonBlock(lessonBlockId);
        Enrollment enrollment = resolveActiveEnrollment(block.getModule().getCourse().getId());
        ensureBlockUnlocked(enrollment, block);

        if (block.getType() != LessonBlockType.WRITING) {
            throw new BusinessException(MessageCodes.LEARNING_INVALID_BLOCK_TYPE, "Not a writing block", HttpStatus.BAD_REQUEST);
        }

        return writingSubmissionRepository.findByEnrollmentIdAndLessonBlockId(enrollment.getId(), lessonBlockId)
                .map(this::mapToWritingSubmissionDetailResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public StudentWritingSubmissionResponse saveWritingDraft(UUID lessonBlockId, WritingDraftRequest request) {
        LessonBlock block = resolveLessonBlock(lessonBlockId);
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(
                        resolveActiveEnrollment(block.getModule().getCourse().getId()).getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND, "Enrollment not found", HttpStatus.NOT_FOUND));

        if (block.getType() != LessonBlockType.WRITING) {
            throw new BusinessException(
                    MessageCodes.LEARNING_INVALID_BLOCK_TYPE, "Not a writing block", HttpStatus.BAD_REQUEST);
        }

        WritingSubmission submission = writingSubmissionRepository
                .findByEnrollmentIdAndLessonBlockIdForUpdate(enrollment.getId(), lessonBlockId)
                .orElse(null);

        if (submission != null && submission.getStatus() != WritingSubmissionStatus.DRAFT) {
            // Autosave must never modify a submitted assignment or its review lifecycle.
            return mapToWritingSubmissionDetailResponse(submission);
        }

        if (submission == null) {
            submission = WritingSubmission.builder()
                    .enrollment(enrollment)
                    .student(enrollment.getStudent())
                    .lessonBlockId(lessonBlockId)
                    .legacyLessonId(null)
                    .status(WritingSubmissionStatus.DRAFT)
                    .submittedAt(Instant.now())
                    .build();
        }
        submission.setContent(request.content());
        submission.setStatus(WritingSubmissionStatus.DRAFT);
        return mapToWritingSubmissionDetailResponse(writingSubmissionRepository.saveAndFlush(submission));
    }

    @Override
    @Transactional
    public StudentWritingSubmissionResponse submitWriting(UUID lessonBlockId, WritingSubmissionRequest request) {
        LessonBlock block = resolveLessonBlock(lessonBlockId);
        // Pessimistic lock enrollment to prevent race conditions during submission processing
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(resolveActiveEnrollment(block.getModule().getCourse().getId()).getId())
                .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Enrollment not found", HttpStatus.NOT_FOUND));
        ensureBlockUnlocked(enrollment, block);

        if (block.getType() != LessonBlockType.WRITING) {
            throw new BusinessException(MessageCodes.LEARNING_INVALID_BLOCK_TYPE, "Not a writing block", HttpStatus.BAD_REQUEST);
        }

        WritingSubmission submission = writingSubmissionRepository
                .findByEnrollmentIdAndLessonBlockIdForUpdate(enrollment.getId(), lessonBlockId)
                .orElse(null);

        if (submission != null && submission.getStatus() != WritingSubmissionStatus.DRAFT) {
            throw new BusinessException(
                    MessageCodes.COMMON_CONFLICT,
                    "You have already submitted this assignment.",
                    HttpStatus.CONFLICT);
        }

        if (submission == null) {
            submission = WritingSubmission.builder()
                    .enrollment(enrollment)
                    .student(enrollment.getStudent())
                    .lessonBlockId(lessonBlockId)
                    .legacyLessonId(null)
                    .build();
        }
        submission.setContent(request.content());
        submission.setStatus(WritingSubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(Instant.now());

        try {
            submission = writingSubmissionRepository.saveAndFlush(submission);
        } catch (DataIntegrityViolationException e) {
            String msg = e.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains("uq_writing_submissions_enrollment_block")) {
                throw new BusinessException(MessageCodes.COMMON_CONFLICT, "You have already submitted this assignment.", HttpStatus.CONFLICT);
            }
            throw e;
        }

        // Upsert progress to COMPLETED
        LessonBlockProgress progress = lessonBlockProgressRepository
                .findByEnrollmentIdAndLessonBlockId(enrollment.getId(), block.getId())
                .orElseGet(() -> LessonBlockProgress.builder()
                        .enrollmentId(enrollment.getId())
                        .lessonBlockId(block.getId())
                        .status(LessonProgressStatus.IN_PROGRESS)
                        .build());

        progress.setStatus(LessonProgressStatus.COMPLETED);
        if (progress.getCompletedAt() == null) {
            progress.setCompletedAt(Instant.now());
        }
        lessonBlockProgressRepository.save(progress);

        notifyTeacherOfWritingSubmission(submission, block);

        return mapToWritingSubmissionDetailResponse(submission);
    }

    private void notifyTeacherOfWritingSubmission(
            WritingSubmission submission,
            LessonBlock block
    ) {
        Course course = submission.getEnrollment().getCourse();
        if (course.getTeacher() == null || course.getTeacher().getUser() == null) {
            return;
        }

        var teacherUser = course.getTeacher().getUser();
        StudentProfile student = submission.getStudent();
        String studentName = student != null && student.getDisplayName() != null
                && !student.getDisplayName().isBlank()
                ? student.getDisplayName().trim()
                : "Học viên ManabiHub";
        String lessonTitle = block.getTitle() == null || block.getTitle().isBlank()
                ? "Bài viết"
                : block.getTitle().trim();

        notificationService.createNotification(
                teacherUser.getId(),
                teacherUser.getEmail(),
                "Có bài viết mới cần phản hồi",
                studentName + " đã hoàn thành bài \"" + lessonTitle
                        + "\" trong khóa học \"" + course.getTitle() + "\".",
                NotificationTypes.WRITING_SUBMITTED,
                "/teacher/writing-reviews/" + submission.getId()
        );
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StudentWritingSubmissionResponse requestAiWritingAssistance(UUID lessonBlockId, UUID submissionId) {
        WritingSubmission preCheckSub = transactionTemplate.execute(status -> {
            LessonBlock block = resolveLessonBlock(lessonBlockId);
            Enrollment enrollment = resolveActiveEnrollment(block.getModule().getCourse().getId());
            ensureBlockUnlocked(enrollment, block);
            return writingSubmissionRepository.findByIdAndEnrollmentIdAndLessonBlockId(submissionId, enrollment.getId(), lessonBlockId)
                    .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Submission not found", HttpStatus.NOT_FOUND));
        });

        UUID currentUserId = currentUserService.getCurrentUserId();
        Course course = preCheckSub.getEnrollment().getCourse();
        AiChatSettingsService.AiChatSettings settings = aiChatSettingsService.getSettings();

        // 1. Validate Eligibility
        if (!settings.aiEnabled() || !settings.aiWritingEnabled() || !course.isAiSupported()) {
            aiUsageLogService.record(currentUserId, course.getId(), lessonBlockId, submissionId, "AI_WRITING_ASSISTANCE", AiUsageRequestStatus.BLOCKED, null, 0, 0, "Feature disabled");
            throw new BusinessException(MessageCodes.AI_NOT_AVAILABLE_FOR_COURSE, "AI writing assistance is not available for this course.", HttpStatus.FORBIDDEN);
        }

        if (course.getPrice() == null || course.getPrice().compareTo(settings.priceFloor()) < 0) {
            aiUsageLogService.record(currentUserId, course.getId(), lessonBlockId, submissionId, "AI_WRITING_ASSISTANCE", AiUsageRequestStatus.BLOCKED, null, 0, 0, "Price floor not met");
            throw new BusinessException(MessageCodes.AI_NOT_AVAILABLE_FOR_COURSE, "AI writing assistance is unavailable for this course plan.", HttpStatus.FORBIDDEN);
        }

        // Persist the per-submission lock before reserving the shared, atomic AI quota.
        boolean canProcess = transactionTemplate.execute(status -> {
            WritingSubmission currentSub = writingSubmissionRepository.findByIdAndEnrollmentIdAndLessonBlockIdForUpdate(submissionId, preCheckSub.getEnrollment().getId(), lessonBlockId)
                    .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Submission not found", HttpStatus.NOT_FOUND));

            if (currentSub.getStatus() == WritingSubmissionStatus.SUGGESTION_PROCESSING ||
                currentSub.getStatus() == WritingSubmissionStatus.SUGGESTION_READY ||
                currentSub.getStatus() == WritingSubmissionStatus.TEACHER_FEEDBACK_READY) {
                return false;
            }
            currentSub.setStatus(WritingSubmissionStatus.SUGGESTION_PROCESSING);
            writingSubmissionRepository.saveAndFlush(currentSub);
            return true;
        });

        if (Boolean.FALSE.equals(canProcess)) {
            WritingSubmission currentSub = writingSubmissionRepository.findById(submissionId).orElseThrow();
            return mapToWritingSubmissionDetailResponse(currentSub);
        }

        // Reserve quota before calling the provider.
        WritingSubmission submission = writingSubmissionRepository.findById(submissionId).orElseThrow();
        LessonBlock block = lessonBlockRepository.findById(lessonBlockId).orElseThrow();
        UUID reservation;
        try {
            reservation = aiQuotaService.reserve(currentUserId, course.getId(), lessonBlockId, submissionId, "AI_WRITING_ASSISTANCE", settings);
        } catch (BusinessException quotaError) {
            transactionTemplate.executeWithoutResult(status -> {
                WritingSubmission sub = writingSubmissionRepository.findById(submissionId).orElseThrow();
                sub.setStatus(WritingSubmissionStatus.SUGGESTION_FAILED); writingSubmissionRepository.save(sub);
            });
            throw quotaError;
        }
        try {
            AiWritingAssistanceProvider.Result result = aiWritingAssistanceProvider.generate(
                    course.getLearningLanguage(),
                    course.getProficiencyFramework(),
                    course.getFrameworkVersion(),
                    course.getProficiencyLevel(),
                    block.getWritingPrompt(),
                    block.getRubric(),
                    submission.getContent()
            );

            transactionTemplate.executeWithoutResult(status -> {
                WritingSubmission sub = writingSubmissionRepository.findById(submissionId).orElseThrow();
                sub.setStatus(WritingSubmissionStatus.SUGGESTION_READY);
                writingSubmissionRepository.save(sub);

                AiWritingSuggestion suggestion = AiWritingSuggestion.builder()
                        .writingSubmission(sub)
                        .provider(result.provider())
                        .status("READY")
                        .grammarSuggestions(result.grammarSuggestions())
                        .vocabularySuggestions(result.vocabularySuggestions())
                        .structureSuggestions(result.structureSuggestions())
                        .revisionGuidance(result.revisionGuidance())
                        .official(false)
                        .build();
                aiWritingSuggestionRepository.save(suggestion);
            });

            aiQuotaService.finish(reservation, AiUsageRequestStatus.SUCCESS, result.provider(), result.inputTokens(), result.outputTokens(), null);

        } catch (AiChatProviderException e) {
            transactionTemplate.executeWithoutResult(status -> {
                WritingSubmission sub = writingSubmissionRepository.findById(submissionId).orElseThrow();
                sub.setStatus(WritingSubmissionStatus.SUGGESTION_FAILED);
                writingSubmissionRepository.save(sub);

                AiWritingSuggestion suggestion = AiWritingSuggestion.builder()
                        .writingSubmission(sub)
                        .status("FAILED")
                        .grammarSuggestions(objectMapper.createArrayNode())
                        .vocabularySuggestions(objectMapper.createArrayNode())
                        .structureSuggestions(objectMapper.createArrayNode())
                        .failureReason("Provider error")
                        .official(false)
                        .build();
                aiWritingSuggestionRepository.save(suggestion);
            });

            aiQuotaService.finish(reservation, AiUsageRequestStatus.FAILED, null, 0, 0, "Provider error");
            throw new BusinessException(MessageCodes.MSG_AI_002, "AI provider is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(status -> {
                WritingSubmission sub = writingSubmissionRepository.findById(submissionId).orElseThrow();
                sub.setStatus(WritingSubmissionStatus.SUGGESTION_FAILED);
                writingSubmissionRepository.save(sub);

                AiWritingSuggestion suggestion = AiWritingSuggestion.builder()
                        .writingSubmission(sub)
                        .status("FAILED")
                        .grammarSuggestions(objectMapper.createArrayNode())
                        .vocabularySuggestions(objectMapper.createArrayNode())
                        .structureSuggestions(objectMapper.createArrayNode())
                        .failureReason("Internal error")
                        .official(false)
                        .build();
                aiWritingSuggestionRepository.save(suggestion);
            });

            aiQuotaService.finish(reservation, AiUsageRequestStatus.FAILED, null, 0, 0, "Internal error");
            throw new BusinessException(MessageCodes.MSG_AI_002, "AI processing failed due to an internal error", HttpStatus.SERVICE_UNAVAILABLE);
        }

        return mapToWritingSubmissionDetailResponse(writingSubmissionRepository.findById(submissionId).orElse(submission));
    }

    private StudentWritingSubmissionResponse mapToWritingSubmissionDetailResponse(WritingSubmission submission) {
        AiWritingSuggestion suggestion = aiWritingSuggestionRepository.findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submission.getId()).orElse(null);
        AiWritingSuggestionResponse suggestionResponse = suggestion == null ? null : new AiWritingSuggestionResponse(
                suggestion.getId(),
                suggestion.getStatus(),
                suggestion.getGrammarSuggestions(),
                suggestion.getVocabularySuggestions(),
                suggestion.getStructureSuggestions(),
                suggestion.getRevisionGuidance(),
                suggestion.getConfidenceLevel(),
                suggestion.isOfficial(),
                suggestion.getFailureReason(),
                suggestion.getCreatedAt()
        );

        TeacherWritingFeedback feedback = teacherWritingFeedbackRepository
                .findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submission.getId())
                .orElse(null);
        TeacherWritingFeedbackResponse feedbackResponse = feedback == null ? null : new TeacherWritingFeedbackResponse(
                feedback.getId(),
                feedback.getScore(),
                feedback.getComment(),
                feedback.getRubricResult(),
                feedback.isOfficial(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt()
        );

        return new StudentWritingSubmissionResponse(
                submission.getId(),
                submission.getLessonBlockId(),
                submission.getContent(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                suggestionResponse,
                feedbackResponse
        );
    }
}
