package com.manabihub.course.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.dto.request.CourseModuleRequest;
import com.manabihub.course.dto.request.FlashcardItemRequest;
import com.manabihub.course.dto.request.LessonBlockRequest;
import com.manabihub.course.dto.request.QuizQuestionRequest;
import com.manabihub.course.dto.request.ReorderRequest;
import com.manabihub.course.dto.response.CourseBuilderResponse;
import com.manabihub.course.dto.response.CourseModuleResponse;
import com.manabihub.course.dto.response.FlashcardItemResponse;
import com.manabihub.course.dto.response.LessonBlockResponse;
import com.manabihub.course.dto.response.QuizQuestionResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseModuleRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.course.service.CourseBuilderService;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseBuilderServiceImpl implements CourseBuilderService {

    private static final int LONG_VIDEO_LIMIT_MINUTES = 15;
    private static final TypeReference<List<String>> QUIZ_OPTIONS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<QuizQuestionResponse>> QUIZ_ITEMS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<FlashcardItemResponse>> FLASHCARDS_TYPE = new TypeReference<>() {
    };

    private final CourseRepository courseRepository;
    private final CourseModuleRepository courseModuleRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final CourseEditDraftService courseEditDraftService;

    @Override
    @Transactional(readOnly = true)
    public CourseBuilderResponse getBuilder(UUID draftId) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        return toResponse(course);
    }

    @Override
    public CourseBuilderResponse createModule(UUID draftId, CourseModuleRequest request) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        String title = validateModuleTitle(request.title());

        CourseModule module = CourseModule.builder()
                .id(courseEditDraftService.hasEditDraft(course.getId()) ? UUID.randomUUID() : null)
                .course(course)
                .title(title)
                .description(trimToNull(request.description()))
                .orderIndex(course.getModules().size() + 1)
                .build();

        course.addModule(module);
        if (!courseEditDraftService.saveIfVersioned(course)) {
            courseModuleRepository.save(module);
        }

        return toResponse(course);
    }

    @Override
    public CourseBuilderResponse updateModule(UUID draftId, UUID moduleId, CourseModuleRequest request) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        CourseModule module = resolveModule(course, moduleId);

        module.setTitle(validateModuleTitle(request.title()));
        module.setDescription(trimToNull(request.description()));
        courseEditDraftService.saveIfVersioned(course);

        return toResponse(course);
    }

    @Override
    public CourseBuilderResponse deleteModule(UUID draftId, UUID moduleId) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        CourseModule module = resolveModule(course, moduleId);

        course.getModules().remove(module);
        if (courseEditDraftService.hasEditDraft(course.getId())) {
            normalizeModuleOrder(course.getModules());
            courseEditDraftService.saveIfVersioned(course);
        } else {
            courseModuleRepository.delete(module);
            courseModuleRepository.flush();
            normalizeModuleOrder(course.getModules());
        }

        return toResponse(course);
    }

    @Override
    public CourseBuilderResponse reorderModules(UUID draftId, ReorderRequest request) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        List<CourseModule> modules = sortedModules(course);
        assertSameIds(request.orderedIds(), modules.stream().map(CourseModule::getId).toList());

        Map<UUID, CourseModule> moduleById = modules.stream()
                .collect(Collectors.toMap(CourseModule::getId, Function.identity()));

        if (courseEditDraftService.hasEditDraft(course.getId())) {
            for (int index = 0; index < request.orderedIds().size(); index++) {
                moduleById.get(request.orderedIds().get(index)).setOrderIndex(index + 1);
            }
            courseEditDraftService.saveIfVersioned(course);
            return toResponse(course);
        }

        for (int index = 0; index < request.orderedIds().size(); index++) {
            moduleById.get(request.orderedIds().get(index)).setOrderIndex(-(index + 1));
        }
        courseModuleRepository.flush();

        for (int index = 0; index < request.orderedIds().size(); index++) {
            moduleById.get(request.orderedIds().get(index)).setOrderIndex(index + 1);
        }

        return toResponse(course);
    }

    @Override
    public CourseBuilderResponse createBlock(UUID draftId, UUID moduleId, LessonBlockRequest request) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        CourseModule module = resolveModule(course, moduleId);
        ValidatedBlockData data = validateBlockRequest(request);
        assertKnowledgeBlockExistsBeforePractice(module, data.type(), null);

        LessonBlock block = LessonBlock.builder()
                .id(courseEditDraftService.hasEditDraft(course.getId()) ? UUID.randomUUID() : null)
                .module(module)
                .orderIndex(module.getBlocks().size() + 1)
                .build();
        applyBlockData(block, data);

        module.addBlock(block);
        if (!courseEditDraftService.saveIfVersioned(course)) {
            lessonBlockRepository.save(block);
        }

        return toResponse(course);
    }

    @Override
    public CourseBuilderResponse updateBlock(UUID draftId, UUID moduleId, UUID blockId, LessonBlockRequest request) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        CourseModule module = resolveModule(course, moduleId);
        LessonBlock block = resolveBlock(module, blockId);
        ValidatedBlockData data = validateBlockRequest(request);
        assertKnowledgeBlockExistsBeforePractice(module, data.type(), block.getId());

        applyBlockData(block, data);
        courseEditDraftService.saveIfVersioned(course);

        return toResponse(course);
    }

    @Override
    public CourseBuilderResponse deleteBlock(UUID draftId, UUID moduleId, UUID blockId) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        CourseModule module = resolveModule(course, moduleId);
        LessonBlock block = resolveBlock(module, blockId);

        module.getBlocks().remove(block);
        if (courseEditDraftService.hasEditDraft(course.getId())) {
            normalizeBlockOrder(module.getBlocks());
            courseEditDraftService.saveIfVersioned(course);
        } else {
            lessonBlockRepository.delete(block);
            lessonBlockRepository.flush();
            normalizeBlockOrder(module.getBlocks());
        }

        return toResponse(course);
    }

    @Override
    public CourseBuilderResponse reorderBlocks(UUID draftId, UUID moduleId, ReorderRequest request) {
        Course course = resolveDraftForCurrentTeacher(draftId);
        CourseModule module = resolveModule(course, moduleId);
        List<LessonBlock> blocks = sortedBlocks(module);
        assertSameIds(request.orderedIds(), blocks.stream().map(LessonBlock::getId).toList());

        Map<UUID, LessonBlock> blockById = blocks.stream()
                .collect(Collectors.toMap(LessonBlock::getId, Function.identity()));

        if (courseEditDraftService.hasEditDraft(course.getId())) {
            for (int index = 0; index < request.orderedIds().size(); index++) {
                blockById.get(request.orderedIds().get(index)).setOrderIndex(index + 1);
            }
            courseEditDraftService.saveIfVersioned(course);
            return toResponse(course);
        }

        for (int index = 0; index < request.orderedIds().size(); index++) {
            blockById.get(request.orderedIds().get(index)).setOrderIndex(-(index + 1));
        }
        lessonBlockRepository.flush();

        for (int index = 0; index < request.orderedIds().size(); index++) {
            blockById.get(request.orderedIds().get(index)).setOrderIndex(index + 1);
        }

        return toResponse(course);
    }

    private Course resolveDraftForCurrentTeacher(UUID draftId) {
        UUID currentUserId = currentUserService.getCurrentUserId();
        TeacherProfile teacherProfile = teacherProfileRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MSG_KYC_010,
                        "Teacher KYC must be approved before configuring course content",
                        HttpStatus.FORBIDDEN
                ));

        if (teacherProfile.getKycStatus() != TeacherKycStatus.PENDING
                && teacherProfile.getKycStatus() != TeacherKycStatus.APPROVED) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_010,
                        "Complete identity and professional qualification submission before configuring course content",
                    HttpStatus.FORBIDDEN
            );
        }

        Course persistedCourse = courseRepository.findByIdAndTeacher_IdAndStatusIn(
                        draftId,
                        teacherProfile.getId(),
                        List.of(CourseStatus.DRAFT, CourseStatus.REJECTED, CourseStatus.FORCED_DRAFT)
                )
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COURSE_NOT_FOUND,
                        "Course draft was not found",
                        HttpStatus.NOT_FOUND
                ));
        return courseEditDraftService.resolveEditableCourse(persistedCourse);
    }

    private CourseModule resolveModule(Course course, UUID moduleId) {
        return course.getModules().stream()
                .filter(module -> module.getId().equals(moduleId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.CONTENT_NOT_FOUND,
                        "Course module was not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private LessonBlock resolveBlock(CourseModule module, UUID blockId) {
        return module.getBlocks().stream()
                .filter(block -> block.getId().equals(blockId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.CONTENT_NOT_FOUND,
                        "Lesson block was not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private String validateModuleTitle(String value) {
        String title = requiredTrim(value, "Module title is required");
        if (title.length() > 120) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Module title must be at most 120 characters");
        }
        return title;
    }

    private ValidatedBlockData validateBlockRequest(LessonBlockRequest request) {
        if (request.type() == null) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Lesson block type is required");
        }

        String title = requiredTrim(request.title(), "Lesson block title is required");
        if (title.length() > 80) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Lesson block title must be at most 80 characters");
        }

        return switch (request.type()) {
            case VIDEO -> validateVideoBlock(request, title);
            case TEXT -> validateTextBlock(request, title);
            case QUIZ -> validateQuizBlock(request, title);
            case FLASHCARD -> validateFlashcardBlock(request, title);
            case WRITING -> validateWritingBlock(request, title);
        };
    }

    private void assertKnowledgeBlockExistsBeforePractice(CourseModule module, LessonBlockType type, UUID currentBlockId) {
        if (!isPracticeBlockType(type)) {
            return;
        }

        boolean hasKnowledgeBlock = module.getBlocks().stream()
                .filter(block -> currentBlockId == null || !block.getId().equals(currentBlockId))
                .anyMatch(block -> block.getType() == LessonBlockType.VIDEO || block.getType() == LessonBlockType.TEXT);

        if (!hasKnowledgeBlock) {
            throw new BusinessException(
                    MessageCodes.VALIDATION_FAILED,
                    "Hãy thêm Video bài giảng hoặc Bài đọc trước khi tạo bài tập tương tác."
            );
        }
    }

    private boolean isPracticeBlockType(LessonBlockType type) {
        return type == LessonBlockType.QUIZ
                || type == LessonBlockType.FLASHCARD
                || type == LessonBlockType.WRITING;
    }

    private ValidatedBlockData validateVideoBlock(LessonBlockRequest request, String title) {
        String videoUrl = requiredTrim(request.videoUrl(), "Video URL is required");
        Integer durationMinutes = request.durationMinutes();
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Video duration must be greater than 0 minutes");
        }

        return new ValidatedBlockData(
                LessonBlockType.VIDEO,
                title,
                trimToNull(request.content()),
                videoUrl,
                durationMinutes,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                null
        );
    }

    private ValidatedBlockData validateTextBlock(LessonBlockRequest request, String title) {
        return new ValidatedBlockData(
                LessonBlockType.TEXT,
                title,
                requiredTrim(request.content(), "Text block content is required"),
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                null
        );
    }

    private ValidatedBlockData validateQuizBlock(LessonBlockRequest request, String title) {
        List<QuizQuestionResponse> quizItems = normalizeQuizItems(request);
        if (quizItems.isEmpty()) {
            throw new BusinessException(MessageCodes.MSG_COURSE_013, "Quiz block must include at least 1 question");
        }

        QuizQuestionResponse firstQuestion = quizItems.getFirst();

        return new ValidatedBlockData(
                LessonBlockType.QUIZ,
                title,
                null,
                null,
                null,
                firstQuestion.question(),
                firstQuestion.options(),
                firstQuestion.answer(),
                quizItems,
                List.of(),
                null,
                null
        );
    }

    private ValidatedBlockData validateFlashcardBlock(LessonBlockRequest request, String title) {
        List<FlashcardItemResponse> flashcards = normalizeFlashcards(request.flashcards());
        if (flashcards.isEmpty()) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Flashcard block must include at least 1 card");
        }

        Set<String> fronts = new HashSet<>();
        for (FlashcardItemResponse flashcard : flashcards) {
            if (!fronts.add(normalizeKey(flashcard.front()))) {
                throw new BusinessException(MessageCodes.MSG_COURSE_010, "Flashcard terms cannot be duplicated");
            }
        }

        return new ValidatedBlockData(
                LessonBlockType.FLASHCARD,
                title,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                flashcards,
                null,
                null
        );
    }

    private ValidatedBlockData validateWritingBlock(LessonBlockRequest request, String title) {
        return new ValidatedBlockData(
                LessonBlockType.WRITING,
                title,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                requiredTrim(request.writingPrompt(), "Writing prompt is required"),
                requiredTrim(request.rubric(), "Writing rubric is required", MessageCodes.MSG_WRITE_005)
        );
    }

    private void applyBlockData(LessonBlock block, ValidatedBlockData data) {
        block.setType(data.type());
        block.setTitle(data.title());
        block.setContent(data.content());
        block.setVideoUrl(data.videoUrl());
        block.setDurationMinutes(data.durationMinutes());
        block.setQuizQuestion(data.quizQuestion());
        block.setQuizOptionsJson(toJson(data.quizOptions()));
        block.setQuizAnswer(data.quizAnswer());
        block.setQuizItemsJson(toJson(data.quizItems()));
        block.setFlashcardsJson(toJson(data.flashcards()));
        block.setWritingPrompt(data.writingPrompt());
        block.setRubric(data.rubric());
    }

    private CourseBuilderResponse toResponse(Course course) {
        List<String> warnings = new ArrayList<>();
        List<CourseModuleResponse> moduleResponses = sortedModules(course).stream()
                .map(module -> toModuleResponse(module, warnings))
                .toList();

        return new CourseBuilderResponse(
                course.getId(),
                course.getTitle(),
                moduleResponses,
                warnings,
                srsTrace()
        );
    }

    private CourseModuleResponse toModuleResponse(CourseModule module, List<String> warnings) {
        List<LessonBlock> blocks = sortedBlocks(module);
        List<LessonBlockResponse> blockResponses = new ArrayList<>();

        for (int index = 0; index < blocks.size(); index++) {
            LessonBlock block = blocks.get(index);
            boolean requiresInteraction = requiresInteractionAfter(block);
            boolean interactionSatisfied = !requiresInteraction || hasInteractionAfter(blocks, index);
            String validationMessage = null;

            if (requiresInteraction && !interactionSatisfied) {
                validationMessage = "Video trên 15 phút cần có Trắc nghiệm, Thẻ ghi nhớ hoặc Bài tập viết ngay sau.";
                warnings.add("Học phần \"" + module.getTitle() + "\" có video \"" + block.getTitle()
                        + "\" dài hơn 15 phút nhưng chưa có bài tập tương tác ngay sau.");
            }

            blockResponses.add(toBlockResponse(block, requiresInteraction, interactionSatisfied, validationMessage));
        }

        return new CourseModuleResponse(
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                module.getOrderIndex(),
                blockResponses
        );
    }

    private LessonBlockResponse toBlockResponse(
            LessonBlock block,
            boolean interactionRequiredAfter,
            boolean interactionSatisfied,
            String validationMessage
    ) {
        List<String> quizOptions = readJsonList(block.getQuizOptionsJson(), QUIZ_OPTIONS_TYPE);
        List<QuizQuestionResponse> quizItems = readQuizItems(block, quizOptions);

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

    private boolean requiresInteractionAfter(LessonBlock block) {
        return block.getType() == LessonBlockType.VIDEO
                && block.getDurationMinutes() != null
                && block.getDurationMinutes() > LONG_VIDEO_LIMIT_MINUTES;
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

    private void normalizeModuleOrder(List<CourseModule> modules) {
        List<CourseModule> sortedModules = modules.stream()
                .sorted(Comparator.comparingInt(CourseModule::getOrderIndex))
                .toList();

        for (int index = 0; index < sortedModules.size(); index++) {
            sortedModules.get(index).setOrderIndex(index + 1);
        }
    }

    private void normalizeBlockOrder(List<LessonBlock> blocks) {
        List<LessonBlock> sortedBlocks = blocks.stream()
                .sorted(Comparator.comparingInt(LessonBlock::getOrderIndex))
                .toList();

        for (int index = 0; index < sortedBlocks.size(); index++) {
            sortedBlocks.get(index).setOrderIndex(index + 1);
        }
    }

    private void assertSameIds(List<UUID> requestedIds, List<UUID> existingIds) {
        if (requestedIds == null || requestedIds.size() != existingIds.size()) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Reorder request does not match current items");
        }

        Set<UUID> requestedSet = new HashSet<>(requestedIds);
        Set<UUID> existingSet = new HashSet<>(existingIds);
        if (requestedSet.size() != requestedIds.size() || !requestedSet.equals(existingSet)) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Reorder request does not match current items");
        }
    }

    private List<CourseModule> sortedModules(Course course) {
        return course.getModules().stream()
                .sorted(Comparator.comparingInt(CourseModule::getOrderIndex))
                .toList();
    }

    private List<LessonBlock> sortedBlocks(CourseModule module) {
        return module.getBlocks().stream()
                .sorted(Comparator.comparingInt(LessonBlock::getOrderIndex))
                .toList();
    }

    private List<String> normalizeTextList(List<String> values) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private List<QuizQuestionResponse> normalizeQuizItems(LessonBlockRequest request) {
        List<QuizQuestionRequest> values = request.quizItems();
        if (values == null || values.isEmpty()) {
            List<String> legacyOptions = normalizeTextList(request.quizOptions());
            if (!StringUtils.hasText(request.quizQuestion())
                    && legacyOptions.isEmpty()
                    && !StringUtils.hasText(request.quizAnswer())) {
                return List.of();
            }
            values = List.of(new QuizQuestionRequest(request.quizQuestion(), legacyOptions, request.quizAnswer()));
        }

        List<QuizQuestionResponse> normalized = new ArrayList<>();
        for (QuizQuestionRequest value : values) {
            if (value == null) {
                throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Quiz question is required");
            }

            String question = requiredTrim(value.question(), "Quiz question is required");
            List<String> options = normalizeTextList(value.options());
            if (options.size() < 2) {
                throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Quiz must have at least 2 options");
            }
            if (hasDuplicateText(options)) {
                throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Quiz options cannot be duplicated");
            }

            String answer = requiredTrim(value.answer(), "Quiz answer key is required", MessageCodes.MSG_COURSE_013);
            String matchedAnswer = options.stream()
                    .filter(option -> normalizeKey(option).equals(normalizeKey(answer)))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            MessageCodes.MSG_COURSE_013,
                            "Quiz answer key must match one of the options"
                    ));

            normalized.add(new QuizQuestionResponse(question, options, matchedAnswer));
        }

        return normalized;
    }

    private List<FlashcardItemResponse> normalizeFlashcards(List<FlashcardItemRequest> values) {
        if (values == null) {
            return List.of();
        }

        List<FlashcardItemResponse> normalized = new ArrayList<>();
        for (FlashcardItemRequest value : values) {
            if (value == null || !StringUtils.hasText(value.front()) || !StringUtils.hasText(value.back())) {
                throw new BusinessException(MessageCodes.VALIDATION_FAILED, "Flashcard front and back are required");
            }
            normalized.add(new FlashcardItemResponse(value.front().trim(), value.back().trim()));
        }

        return normalized;
    }

    private List<QuizQuestionResponse> readQuizItems(LessonBlock block, List<String> fallbackOptions) {
        List<QuizQuestionResponse> quizItems = readJsonList(block.getQuizItemsJson(), QUIZ_ITEMS_TYPE);
        if (!quizItems.isEmpty()) {
            return quizItems;
        }

        if (StringUtils.hasText(block.getQuizQuestion())) {
            return List.of(new QuizQuestionResponse(
                    block.getQuizQuestion(),
                    fallbackOptions,
                    block.getQuizAnswer()
            ));
        }

        return List.of();
    }

    private boolean hasDuplicateText(List<String> values) {
        Set<String> keys = new HashSet<>();
        for (String value : values) {
            if (!keys.add(normalizeKey(value))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredTrim(String value, String message) {
        return requiredTrim(value, message, MessageCodes.VALIDATION_FAILED);
    }

    private String requiredTrim(String value, String message, String messageCode) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(messageCode, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Cannot serialize lesson block content",
                    exception
            );
        }
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

    private Map<String, Object> srsTrace() {
        return Map.of(
                "uc", "UC-34",
                "br", List.of(
                        "BR-PROD-04",
                        "BR-PROD-05",
                        "BR-COURSE-03",
                        "BR-COURSE-06",
                        "BR-CONTENT-01",
                        "BR-CONTENT-02",
                        "BR-CONTENT-03",
                        "BR-CONTENT-04",
                        "NUI-VID-01"
                ),
                "msg", List.of(
                        MessageCodes.CONTENT_CREATED,
                        MessageCodes.CONTENT_UPDATED,
                        MessageCodes.CONTENT_DELETED,
                        MessageCodes.MSG_COURSE_001,
                        MessageCodes.MSG_KYC_010,
                        MessageCodes.MSG_COURSE_009,
                        MessageCodes.MSG_COURSE_010,
                        MessageCodes.MSG_COURSE_013,
                        MessageCodes.MSG_WRITE_005
                )
        );
    }

    private record ValidatedBlockData(
            LessonBlockType type,
            String title,
            String content,
            String videoUrl,
            Integer durationMinutes,
            String quizQuestion,
            List<String> quizOptions,
            String quizAnswer,
            List<QuizQuestionResponse> quizItems,
            List<FlashcardItemResponse> flashcards,
            String writingPrompt,
            String rubric
    ) {
    }
}
