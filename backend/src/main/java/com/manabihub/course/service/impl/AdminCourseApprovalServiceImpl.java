package com.manabihub.course.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.exception.ValidationBusinessException;
import com.manabihub.course.dto.request.CourseReviewRequest;
import com.manabihub.course.dto.response.CourseApprovalCriterionResponse;
import com.manabihub.course.dto.response.CourseApprovalDetailResponse;
import com.manabihub.course.dto.response.CourseApprovalQueueResponse;
import com.manabihub.course.dto.response.CourseModuleResponse;
import com.manabihub.course.dto.response.FlashcardItemResponse;
import com.manabihub.course.dto.response.LessonBlockResponse;
import com.manabihub.course.dto.response.QuizQuestionResponse;
import com.manabihub.course.dto.response.ValidationError;
import com.manabihub.course.dto.response.ValidationResultResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseApprovalDecision;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.CourseApprovalDecisionEnum;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseApprovalDecisionRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.service.AdminCourseApprovalService;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.course.service.CourseValidationService;
import com.manabihub.finaltest.dto.request.FinalTestChoiceDto;
import com.manabihub.finaltest.dto.request.FinalTestQuestionDto;
import com.manabihub.finaltest.dto.response.FinalTestResponse;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminCourseApprovalServiceImpl implements AdminCourseApprovalService {

    private static final int LONG_VIDEO_LIMIT_MINUTES = 15;
    private static final TypeReference<List<String>> QUIZ_OPTIONS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<QuizQuestionResponse>> QUIZ_ITEMS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<FlashcardItemResponse>> FLASHCARDS_TYPE = new TypeReference<>() {
    };

    private final CourseRepository courseRepository;
    private final CourseApprovalDecisionRepository decisionRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final CourseEditDraftService courseEditDraftService;
    private final CourseValidationService courseValidationService;

    private String requireReviewerRole(UUID adminId) {
        if (courseRepository.hasAdminRole(adminId, List.of("COURSE_MANAGER"))) {
            return "COURSE_MANAGER";
        }
        throw new BusinessException(MessageCodes.ADMIN_PERMISSION_DENIED,
                "Access denied: Course Manager privileges required");
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseApprovalQueueResponse> getQueue(UUID adminId) {
        requireReviewerRole(adminId);
        List<Course> courses = courseRepository.findAllByStatusOrderBySubmittedAtAsc(CourseStatus.PENDING);
        return courses.stream().map(this::mapToQueueResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CourseApprovalDetailResponse getDetail(UUID adminId, UUID courseId) {
        requireReviewerRole(adminId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException("MSG-COM-001", "Course not found"));
        Course reviewCourse = courseEditDraftService.resolveEditableCourse(course);
        List<CourseModule> modules = sortedModules(reviewCourse);
        int lessonBlocksCount = modules.stream()
                .mapToInt(module -> safeBlocks(module).size())
                .sum();
        int totalVideoDurationMinutes = modules.stream()
                .flatMap(module -> safeBlocks(module).stream())
                .filter(block -> block.getType() == LessonBlockType.VIDEO)
                .map(LessonBlock::getDurationMinutes)
                .filter(duration -> duration != null && duration > 0)
                .mapToInt(Integer::intValue)
                .sum();
        FinalTest finalTest = reviewCourse.getFinalTest();
        List<ValidationError> validationErrors = collectApprovalErrors(
                course,
                courseValidationService.validateCourseForReview(courseId)
        );

        return CourseApprovalDetailResponse.builder()
                .id(course.getId())
                .courseName(reviewCourse.getTitle())
                .teacherName(course.getTeacher().getUser().getFullName())
                .teacherEmail(course.getTeacher().getUser().getEmail())
                .submittedAt(course.getSubmittedAt())
                .status(course.getStatus())
                .curriculumSummary(reviewCourse.getDescription())
                .introduction(reviewCourse.getIntroduction())
                .jlptLevel(reviewCourse.getJlptLevel())
                .category(reviewCourse.getCategory())
                .thumbnailUrl(reviewCourse.getThumbnailUrl())
                .outcomes(reviewCourse.getOutcomes())
                .price(reviewCourse.getPrice())
                .currency(reviewCourse.getCurrency())
                .prerequisites(reviewCourse.getPrerequisites())
                .targetStudents(reviewCourse.getTargetStudents())
                .moduleCount(modules.size())
                .lessonBlocksCount(lessonBlocksCount)
                .totalVideoDurationMinutes(totalVideoDurationMinutes)
                .finalTestIncluded(finalTest != null)
                .previousDecisionReason(course.getRejectionReason())
                .teacherKycStatus(course.getTeacher().getKycStatus() == null
                        ? null
                        : course.getTeacher().getKycStatus().name())
                .teacherCanPublish(course.getTeacher().isCanPublishCourse())
                .approvalReady(validationErrors.isEmpty())
                .learningGoals(reviewCourse.getLearningGoals() == null
                        ? List.of()
                        : reviewCourse.getLearningGoals().stream()
                                .sorted(Comparator.comparingInt(goal -> goal.getOrderIndex()))
                                .map(goal -> goal.getGoalText())
                                .toList())
                .modules(modules.stream().map(this::toModuleResponse).toList())
                .finalTest(toFinalTestResponse(finalTest, reviewCourse.getId()))
                .validationErrors(validationErrors)
                .reviewCriteria(buildReviewCriteria(validationErrors))
                .build();
    }

    @Override
    @Transactional
    public void reviewCourse(UUID adminId, UUID courseId, CourseReviewRequest request) {
        String actorRoleCode = requireReviewerRole(adminId);

        Course course = courseRepository.findByIdForApprovalReview(courseId)
                .orElseThrow(() -> new BusinessException("MSG-COM-001", "Không tìm thấy khóa học."));

        if (course.getStatus() != CourseStatus.PENDING) {
            throw new BusinessException(
                    "MSG-COURSE-REVIEW-STATE",
                    "Yêu cầu xét duyệt này đã được xử lý. Chỉ khóa học đang chờ phê duyệt mới có thể nhận quyết định.",
                    HttpStatus.CONFLICT
            );
        }
        Course reviewCourse = courseEditDraftService.resolveEditableCourse(course);

        CourseStatus oldStatus = course.getStatus();
        CourseApprovalDecisionEnum decisionEnum;
        String actionLog;
        String notificationMessage;

        switch (request.getAction().toUpperCase()) {
            case "APPROVE":
                List<ValidationError> validationErrors = collectApprovalErrors(
                        course,
                        courseValidationService.validateCourseForReview(courseId)
                );
                if (!validationErrors.isEmpty()) {
                    throw new ValidationBusinessException(
                            "MSG-COURSE-004",
                            "Khóa học chưa đáp ứng đầy đủ điều kiện phê duyệt.",
                            validationErrors
                    );
                }
                courseEditDraftService.applyApprovedDraft(course);
                course.setStatus(CourseStatus.APPROVED);
                course.setApprovedBy(adminId);
                course.setApprovedAt(Instant.now());
                course.setRejectionReason(null);
                decisionEnum = CourseApprovalDecisionEnum.APPROVED;
                actionLog = "COURSE_APPROVED";
                notificationMessage = "Khóa học \"" + course.getTitle()
                        + "\" đã được phê duyệt và sẵn sàng để xuất bản.";
                break;
            case "REJECT":
                if (request.getReason() == null || request.getReason().isBlank()) {
                    throw new BusinessException("MSG-COM-002", "Reason is required for rejection");
                }
                course.setStatus(CourseStatus.REJECTED);
                course.setRejectionReason(request.getReason());
                decisionEnum = CourseApprovalDecisionEnum.REJECTED;
                actionLog = "COURSE_REJECTED";
                notificationMessage = "Khóa học \"" + reviewCourse.getTitle()
                        + "\" đã bị từ chối. Lý do: " + request.getReason();
                break;
            case "REQUEST_CORRECTION":
                if (request.getReason() == null || request.getReason().isBlank()) {
                    throw new BusinessException("MSG-COM-002", "Reason is required for correction request");
                }
                course.setStatus(CourseStatus.DRAFT);
                course.setRejectionReason(request.getReason());
                decisionEnum = CourseApprovalDecisionEnum.CORRECTION_REQUIRED;
                actionLog = "COURSE_CORRECTION_REQUESTED";
                notificationMessage = "Khóa học \"" + reviewCourse.getTitle()
                        + "\" cần được chỉnh sửa trước khi phê duyệt. Nội dung cần sửa: "
                        + request.getReason();
                break;
            default:
                throw new BusinessException("MSG-COM-004", "Invalid action");
        }

        courseRepository.save(course);

        // Save Decision Record
        CourseApprovalDecision decision = CourseApprovalDecision.builder()
                .courseId(course.getId())
                .decidedBy(adminId)
                .decision(decisionEnum)
                .reason(request.getReason())
                .build();
        decisionRepository.save(decision);

        // Create Notification
        notificationService.createNotification(
                course.getTeacher().getUser().getId(),
                course.getTeacher().getUser().getEmail(),
                "Cập nhật kết quả duyệt khóa học",
                notificationMessage,
                NotificationTypes.COURSE_APPROVAL,
                "/teacher/courses"
        );

        // Create Audit Log
        Map<String, Object> metadata = null;
        if (request.getReason() != null && !request.getReason().isBlank()) {
            metadata = Map.of("reason", request.getReason());
        }

        AuditLog auditLog = AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(adminId)
                .actorRoleCode(actorRoleCode)
                .action(actionLog)
                .targetType("COURSE")
                .targetId(course.getId())
                .beforeValue(Map.of("status", oldStatus.name()))
                .afterValue(Map.of("status", course.getStatus().name()))
                .metadata(metadata)
                .build();
        auditLogRepository.save(auditLog);
    }

    private CourseApprovalQueueResponse mapToQueueResponse(Course course) {
        Course reviewCourse = courseEditDraftService.resolveEditableCourse(course);
        return CourseApprovalQueueResponse.builder()
                .id(course.getId())
                .courseName(reviewCourse.getTitle())
                .teacherName(course.getTeacher().getUser().getFullName())
                .teacherEmail(course.getTeacher().getUser().getEmail())
                .submittedAt(course.getSubmittedAt())
                .status(course.getStatus())
                .build();
    }

    private List<ValidationError> collectApprovalErrors(Course course, ValidationResultResponse result) {
        List<ValidationError> errors = new ArrayList<>(result.errors());
        if (course.getTeacher() == null || course.getTeacher().getKycStatus() != TeacherKycStatus.APPROVED) {
            errors.add(new ValidationError(
                    "MSG-KYC-APPROVAL-001",
                    "Giảng viên chưa hoàn tất hoặc không còn hiệu lực xác minh danh tính.",
                    "error"
            ));
        }
        if (course.getTeacher() == null || !course.getTeacher().isCanPublishCourse()) {
            errors.add(new ValidationError(
                    "MSG-KYC-APPROVAL-002",
                    "Tài khoản giảng viên chưa được cấp quyền xuất bản khóa học.",
                    "error"
            ));
        }
        return errors;
    }

    private List<CourseApprovalCriterionResponse> buildReviewCriteria(List<ValidationError> errors) {
        return List.of(
                criterion("TEACHER", "Điều kiện giảng viên",
                        "Danh tính đã được xác minh và tài khoản có quyền xuất bản.", errors,
                        code -> code.startsWith("MSG-KYC-APPROVAL-")),
                criterion("METADATA", "Thông tin khóa học",
                        "Tiêu đề, cấp độ, danh mục, ảnh bìa và giá bán hợp lệ.", errors,
                        code -> List.of("MSG-COURSE-003", "MSG-COURSE-004", "MSG-COURSE-020", "MSG-COURSE-021")
                                .contains(code)),
                criterion("AUDIENCE", "Mục tiêu và đối tượng học",
                        "Có đủ mục tiêu học tập, yêu cầu đầu vào và đối tượng phù hợp.", errors,
                        code -> code.startsWith("MSG-GOAL-")),
                criterion("CURRICULUM", "Cấu trúc chương trình",
                        "Đủ bài học, thời lượng video và không có học phần rỗng.", errors,
                        code -> List.of("MSG-COURSE-015", "MSG-COURSE-016", "MSG-COURSE-017").contains(code)),
                criterion("CONTENT", "Chất lượng nội dung",
                        "Nội dung tiếng Nhật, hoạt động tương tác và bài viết được cấu hình hợp lệ.", errors,
                        code -> (code.startsWith("MSG-COURSE-")
                                && !List.of("MSG-COURSE-003", "MSG-COURSE-004", "MSG-COURSE-015",
                                        "MSG-COURSE-016", "MSG-COURSE-017", "MSG-COURSE-020",
                                        "MSG-COURSE-021").contains(code))
                                || code.startsWith("MSG-WRITE-")),
                criterion("FINAL_TEST", "Bài kiểm tra cuối khóa",
                        "Cấu hình, số câu hỏi, đáp án và giải thích đáp ứng quy định.", errors,
                        code -> code.startsWith("MSG-FINAL-"))
        );
    }

    private CourseApprovalCriterionResponse criterion(
            String code,
            String title,
            String description,
            List<ValidationError> errors,
            Predicate<String> matcher
    ) {
        List<String> reasons = errors.stream()
                .filter(error -> matcher.test(error.code()))
                .map(ValidationError::message)
                .distinct()
                .toList();
        return new CourseApprovalCriterionResponse(code, title, description, reasons.isEmpty(), reasons);
    }

    private List<CourseModule> sortedModules(Course course) {
        if (course.getModules() == null) {
            return List.of();
        }
        return course.getModules().stream()
                .sorted(Comparator.comparingInt(CourseModule::getOrderIndex))
                .toList();
    }

    private List<LessonBlock> safeBlocks(CourseModule module) {
        if (module.getBlocks() == null) {
            return List.of();
        }
        return module.getBlocks().stream()
                .sorted(Comparator.comparingInt(LessonBlock::getOrderIndex))
                .toList();
    }

    private CourseModuleResponse toModuleResponse(CourseModule module) {
        List<LessonBlock> blocks = safeBlocks(module);
        List<LessonBlockResponse> responses = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            LessonBlock block = blocks.get(index);
            boolean requiresInteraction = block.getType() == LessonBlockType.VIDEO
                    && block.getDurationMinutes() != null
                    && block.getDurationMinutes() > LONG_VIDEO_LIMIT_MINUTES;
            boolean interactionSatisfied = !requiresInteraction || hasInteractionAfter(blocks, index);
            responses.add(toBlockResponse(
                    block,
                    requiresInteraction,
                    interactionSatisfied,
                    interactionSatisfied ? null : "Video trên 15 phút cần có hoạt động tương tác ngay sau."
            ));
        }
        return new CourseModuleResponse(
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                module.getOrderIndex(),
                responses
        );
    }

    private boolean hasInteractionAfter(List<LessonBlock> blocks, int currentIndex) {
        if (currentIndex >= blocks.size() - 1) {
            return false;
        }
        LessonBlockType nextType = blocks.get(currentIndex + 1).getType();
        return nextType == LessonBlockType.QUIZ
                || nextType == LessonBlockType.FLASHCARD
                || nextType == LessonBlockType.WRITING;
    }

    private LessonBlockResponse toBlockResponse(
            LessonBlock block,
            boolean interactionRequiredAfter,
            boolean interactionSatisfied,
            String validationMessage
    ) {
        List<String> quizOptions = readJsonList(block.getQuizOptionsJson(), QUIZ_OPTIONS_TYPE);
        List<QuizQuestionResponse> quizItems = readJsonList(block.getQuizItemsJson(), QUIZ_ITEMS_TYPE);
        if (quizItems.isEmpty() && block.getQuizQuestion() != null && !block.getQuizQuestion().isBlank()) {
            quizItems = List.of(new QuizQuestionResponse(
                    block.getQuizQuestion(),
                    quizOptions,
                    block.getQuizAnswer()
            ));
        }
        return new LessonBlockResponse(
                block.getId(),
                block.getType(),
                block.getTitle(),
                block.getContent(),
                block.getVideoUrl(),
                block.getDurationMinutes(),
                block.getQuizQuestion(),
                quizOptions,
                block.getQuizAnswer(),
                quizItems,
                readJsonList(block.getFlashcardsJson(), FLASHCARDS_TYPE),
                block.getWritingPrompt(),
                block.getRubric(),
                block.getOrderIndex(),
                interactionRequiredAfter,
                interactionSatisfied,
                validationMessage
        );
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private FinalTestResponse toFinalTestResponse(FinalTest finalTest, UUID courseId) {
        if (finalTest == null) {
            return null;
        }
        List<FinalTestQuestionDto> questions = finalTest.getQuestions() == null
                ? List.of()
                : finalTest.getQuestions().stream()
                        .sorted(Comparator.comparing(
                                question -> question.getOrderIndex() == null ? Integer.MAX_VALUE : question.getOrderIndex()))
                        .map(question -> FinalTestQuestionDto.builder()
                                .id(question.getId())
                                .content(question.getContent())
                                .explanation(question.getExplanation())
                                .choices(question.getChoices() == null
                                        ? List.of()
                                        : question.getChoices().stream()
                                                .sorted(Comparator.comparing(
                                                        choice -> choice.getOrderIndex() == null
                                                                ? Integer.MAX_VALUE
                                                                : choice.getOrderIndex()))
                                                .map(choice -> FinalTestChoiceDto.builder()
                                                        .id(choice.getId())
                                                        .content(choice.getContent())
                                                        .isCorrect(choice.getIsCorrect())
                                                        .build())
                                                .toList())
                                .build())
                        .toList();
        return FinalTestResponse.builder()
                .id(finalTest.getId())
                .courseId(courseId)
                .timeLimitMinutes(finalTest.getTimeLimitMinutes())
                .passingScore(finalTest.getPassingScore())
                .maxRetakes(finalTest.getMaxRetakes())
                .jlptLevel(finalTest.getJlptLevel())
                .skillFocus(finalTest.getSkillFocus())
                .questions(questions)
                .build();
    }
}
