package com.manabihub.course.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.dto.request.CourseModuleRequest;
import com.manabihub.course.dto.request.FlashcardItemRequest;
import com.manabihub.course.dto.request.LessonBlockRequest;
import com.manabihub.course.dto.request.QuizQuestionRequest;
import com.manabihub.course.dto.request.ReorderRequest;
import com.manabihub.course.dto.response.CourseBuilderResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseModuleRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseBuilderServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseModuleRepository courseModuleRepository;

    @Mock
    private LessonBlockRepository lessonBlockRepository;

    @Mock
    private TeacherProfileRepository teacherProfileRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private CourseEditDraftService courseEditDraftService;

    private CourseBuilderServiceImpl service;
    private UUID userId;
    private TeacherProfile approvedTeacher;
    private Course draft;

    @BeforeEach
    void setUp() {
        service = new CourseBuilderServiceImpl(
                courseRepository,
                courseModuleRepository,
                lessonBlockRepository,
                teacherProfileRepository,
                currentUserService,
                new ObjectMapper(),
                courseEditDraftService
        );
        org.mockito.Mockito.lenient()
                .when(courseEditDraftService.resolveEditableCourse(any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userId = UUID.randomUUID();
        approvedTeacher = new TeacherProfile();
        approvedTeacher.setId(UUID.randomUUID());
        approvedTeacher.setKycStatus(TeacherKycStatus.APPROVED);
        approvedTeacher.setCanPublishCourse(true);

        draft = Course.builder()
                .id(UUID.randomUUID())
                .teacher(approvedTeacher)
                .title("JLPT N5 Foundation")
                .slug("jlpt-n5-foundation")
                .introduction("Introductory Japanese course")
                .jlptLevel(JlptLevel.N5)
                .category("GRAMMAR")
                .outcomes("Understand N5 basics")
                .price(BigDecimal.ZERO)
                .currency("VND")
                .prerequisites("No prerequisites")
                .targetStudents("New learners")
                .status(CourseStatus.DRAFT)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_IdAndStatusIn(
                draft.getId(),
                approvedTeacher.getId(),
                List.of(CourseStatus.DRAFT, CourseStatus.REJECTED, CourseStatus.FORCED_DRAFT)
        ))
                .thenReturn(Optional.of(draft));
    }

    @Test
    void createModule_WhenValid_ShouldAppendModule() {
        when(courseModuleRepository.save(any(CourseModule.class))).thenAnswer(invocation -> {
            CourseModule module = invocation.getArgument(0);
            module.setId(UUID.randomUUID());
            return module;
        });

        CourseBuilderResponse response = service.createModule(
                draft.getId(),
                new CourseModuleRequest("Bài 1: Làm quen N5", "Học phần mở đầu")
        );

        assertEquals(1, response.modules().size());
        assertEquals("Bài 1: Làm quen N5", response.modules().getFirst().title());
        assertEquals("UC-34", response.srsTrace().get("uc"));
        assertTrue(response.srsTrace().toString().contains("BR-CONTENT-01"));
        assertTrue(response.srsTrace().toString().contains("BR-CONTENT-04"));
        assertTrue(response.srsTrace().toString().contains(MessageCodes.MSG_COURSE_013));
    }

    @Test
    void createBlock_WhenQuizHasNoAnswerKey_ShouldThrowValidationError() {
        CourseModule module = module("Bài 1", 1);
        draft.addModule(module);

        LessonBlockRequest request = new LessonBlockRequest(
                LessonBlockType.QUIZ,
                "Câu hỏi kiểm tra",
                null,
                null,
                null,
                "はじめまして nghĩa là gì?",
                List.of("Xin chào lần đầu gặp", "Tạm biệt"),
                "",
                null,
                null,
                null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createBlock(draft.getId(), module.getId(), request)
        );

        assertEquals(MessageCodes.MSG_COURSE_013, exception.getMessageCode());
    }

    @Test
    void createBlock_WhenQuizHasMultipleQuestions_ShouldReturnQuizItems() {
        CourseModule module = module("Bài 1", 1);
        module.addBlock(block(LessonBlockType.TEXT, "Nội dung nền", 1));
        draft.addModule(module);
        when(lessonBlockRepository.save(any(LessonBlock.class))).thenAnswer(invocation -> {
            LessonBlock block = invocation.getArgument(0);
            block.setId(UUID.randomUUID());
            return block;
        });

        LessonBlockRequest request = new LessonBlockRequest(
                LessonBlockType.QUIZ,
                "Bộ câu hỏi ôn tập",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new QuizQuestionRequest("はじめまして nghĩa là gì?", List.of("Xin chào lần đầu gặp", "Tạm biệt"), "Xin chào lần đầu gặp"),
                        new QuizQuestionRequest("Arigatou nghĩa là gì?", List.of("Cảm ơn", "Xin lỗi"), "Cảm ơn")
                ),
                null,
                null,
                null
        );

        CourseBuilderResponse response = service.createBlock(draft.getId(), module.getId(), request);

        assertEquals(2, response.modules().getFirst().blocks().get(1).quizItems().size());
        assertEquals("Cảm ơn", response.modules().getFirst().blocks().get(1).quizItems().get(1).answer());
    }

    @Test
    void createBlock_WhenFlashcardTermIsDuplicated_ShouldThrowValidationError() {
        CourseModule module = module("Bài 1", 1);
        draft.addModule(module);

        LessonBlockRequest request = new LessonBlockRequest(
                LessonBlockType.FLASHCARD,
                "Từ vựng chào hỏi",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new FlashcardItemRequest("こんにちは", "Xin chào"),
                        new FlashcardItemRequest("こんにちは", "Chào buổi trưa")
                ),
                null,
                null
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createBlock(draft.getId(), module.getId(), request)
        );

        assertEquals(MessageCodes.MSG_COURSE_010, exception.getMessageCode());
    }

    @Test
    void createBlock_WhenWritingRubricMissing_ShouldThrowValidationError() {
        CourseModule module = module("Bài 1", 1);
        draft.addModule(module);

        LessonBlockRequest request = new LessonBlockRequest(
                LessonBlockType.WRITING,
                "Viết đoạn tự giới thiệu",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Viết 5 câu tự giới thiệu bản thân.",
                ""
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createBlock(draft.getId(), module.getId(), request)
        );

        assertEquals(MessageCodes.MSG_WRITE_005, exception.getMessageCode());
    }

    @Test
    void getBuilder_WhenLongVideoHasNoInteractionAfter_ShouldReturnWarning() {
        CourseModule module = module("Bài 1", 1);
        LessonBlock video = block(LessonBlockType.VIDEO, "Video bài giảng dài", 1);
        video.setDurationMinutes(20);
        module.addBlock(video);
        draft.addModule(module);

        CourseBuilderResponse response = service.getBuilder(draft.getId());

        assertFalse(response.validationWarnings().isEmpty());
        assertTrue(response.modules().getFirst().blocks().getFirst().interactionRequiredAfter());
        assertFalse(response.modules().getFirst().blocks().getFirst().interactionSatisfied());
    }

    @Test
    void reorderBlocks_WhenLongVideoIsFollowedByQuiz_ShouldSatisfyInteractionRule() {
        CourseModule module = module("Bài 1", 1);
        LessonBlock quiz = block(LessonBlockType.QUIZ, "Quiz tương tác", 1);
        LessonBlock video = block(LessonBlockType.VIDEO, "Video bài giảng dài", 2);
        video.setDurationMinutes(20);
        module.addBlock(quiz);
        module.addBlock(video);
        draft.addModule(module);

        CourseBuilderResponse response = service.reorderBlocks(
                draft.getId(),
                module.getId(),
                new ReorderRequest(List.of(video.getId(), quiz.getId()))
        );

        assertTrue(response.validationWarnings().isEmpty());
        assertTrue(response.modules().getFirst().blocks().getFirst().interactionSatisfied());
    }

    private CourseModule module(String title, int orderIndex) {
        return CourseModule.builder()
                .id(UUID.randomUUID())
                .title(title)
                .description("Module description")
                .orderIndex(orderIndex)
                .build();
    }

    private LessonBlock block(LessonBlockType type, String title, int orderIndex) {
        return LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(type)
                .title(title)
                .content(type == LessonBlockType.TEXT ? "Text content" : null)
                .quizQuestion(type == LessonBlockType.QUIZ ? "Question" : null)
                .quizOptionsJson(type == LessonBlockType.QUIZ ? "[\"A\",\"B\"]" : "[]")
                .quizAnswer(type == LessonBlockType.QUIZ ? "A" : null)
                .flashcardsJson("[]")
                .orderIndex(orderIndex)
                .build();
    }
}
