package com.manabihub.course.revision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseEditDraft;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseEditDraftRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.finaltest.entity.FinalTestChoice;
import com.manabihub.finaltest.entity.FinalTestQuestion;
import com.manabihub.finaltest.repository.FinalTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseEditDraftServiceTest {

    @Mock
    private CourseEditDraftRepository editDraftRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private FinalTestRepository finalTestRepository;

    private CourseEditDraftService service;
    private AtomicReference<CourseEditDraft> storedDraft;

    @BeforeEach
    void setUp() {
        storedDraft = new AtomicReference<>();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new CourseEditDraftService(
                editDraftRepository,
                courseRepository,
                finalTestRepository,
                objectMapper
        );

        when(editDraftRepository.existsById(any(UUID.class)))
                .thenAnswer(invocation -> storedDraft.get() != null);
        when(editDraftRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(storedDraft.get()));
        when(editDraftRepository.save(any(CourseEditDraft.class)))
                .thenAnswer(invocation -> {
                    CourseEditDraft draft = invocation.getArgument(0);
                    storedDraft.set(draft);
                    return draft;
                });
        when(finalTestRepository.findByCourseId(any(UUID.class))).thenReturn(Optional.empty());
        when(finalTestRepository.save(any(FinalTest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void editedRevision_DoesNotMutateStudentVersion_UntilApproval() {
        UUID courseId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Course liveCourse = Course.builder()
                .id(courseId)
                .title("Nội dung đã duyệt")
                .slug("noi-dung-da-duyet")
                .description("Mô tả cũ")
                .introduction("Mô tả cũ")
                .price(BigDecimal.ZERO)
                .currency("VND")
                .status(CourseStatus.PUBLISHED)
                .publishedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
        liveCourse.addLearningGoal("Mục tiêu cũ", 1);

        CourseModule liveModule = CourseModule.builder()
                .id(moduleId)
                .course(liveCourse)
                .title("Học phần đã duyệt")
                .orderIndex(1)
                .build();
        liveModule.addBlock(LessonBlock.builder()
                .id(blockId)
                .type(LessonBlockType.TEXT)
                .title("Bài học đã duyệt")
                .content("Dữ liệu học viên đang học")
                .orderIndex(1)
                .build());
        liveCourse.addModule(liveModule);

        FinalTest liveFinalTest = FinalTest.builder()
                .id(UUID.randomUUID())
                .course(liveCourse)
                .timeLimitMinutes(30)
                .passingScore(80)
                .maxRetakes(3)
                .jlptLevel(com.manabihub.course.enums.JlptLevel.N5)
                .skillFocus("Tổng hợp")
                .build();
        FinalTestQuestion liveQuestion = FinalTestQuestion.builder()
                .id(UUID.randomUUID())
                .finalTest(liveFinalTest)
                .content("Câu hỏi đã duyệt")
                .orderIndex(0)
                .build();
        liveQuestion.getChoices().add(FinalTestChoice.builder()
                .id(UUID.randomUUID())
                .question(liveQuestion)
                .content("Đáp án cũ")
                .isCorrect(true)
                .orderIndex(0)
                .build());
        liveFinalTest.getQuestions().add(liveQuestion);
        liveCourse.setFinalTest(liveFinalTest);
        when(finalTestRepository.findByCourseId(courseId)).thenReturn(Optional.of(liveFinalTest));

        service.beginEditingPublishedCourse(liveCourse);
        Course teacherDraft = service.resolveEditableCourse(liveCourse);
        assertNotSame(liveCourse, teacherDraft);

        teacherDraft.setTitle("Nội dung đang chờ duyệt");
        teacherDraft.getModules().getFirst().getBlocks().getFirst()
                .setContent("Dữ liệu mới chưa được duyệt");
        teacherDraft.getFinalTest().getQuestions().getFirst()
                .setContent("Câu hỏi mới chưa được duyệt");
        assertTrue(service.saveIfVersioned(teacherDraft));
        assertNotNull(service.resolveLastModifiedAt(liveCourse));

        // Student-facing aggregate is still the live, approved version.
        assertEquals("Nội dung đã duyệt", liveCourse.getTitle());
        assertEquals("Dữ liệu học viên đang học",
                liveCourse.getModules().getFirst().getBlocks().getFirst().getContent());
        assertEquals("Câu hỏi đã duyệt",
                liveCourse.getFinalTest().getQuestions().getFirst().getContent());

        // Teacher/reviewer sees the isolated proposed revision.
        Course reviewerView = service.resolveEditableCourse(liveCourse);
        assertEquals("Nội dung đang chờ duyệt", reviewerView.getTitle());
        assertEquals("Dữ liệu mới chưa được duyệt",
                reviewerView.getModules().getFirst().getBlocks().getFirst().getContent());
        assertEquals("Câu hỏi mới chưa được duyệt",
                reviewerView.getFinalTest().getQuestions().getFirst().getContent());

        assertTrue(service.applyApprovedDraft(liveCourse));

        // Approval atomically promotes the revision while preserving stable IDs.
        assertEquals("Nội dung đang chờ duyệt", liveCourse.getTitle());
        assertEquals(moduleId, liveCourse.getModules().getFirst().getId());
        assertEquals(blockId, liveCourse.getModules().getFirst().getBlocks().getFirst().getId());
        assertEquals("Dữ liệu mới chưa được duyệt",
                liveCourse.getModules().getFirst().getBlocks().getFirst().getContent());
        assertEquals("Câu hỏi mới chưa được duyệt",
                liveCourse.getFinalTest().getQuestions().getFirst().getContent());
    }
}
