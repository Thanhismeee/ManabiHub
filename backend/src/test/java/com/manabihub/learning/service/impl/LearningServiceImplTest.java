package com.manabihub.learning.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.CourseStatus;
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
import com.manabihub.learning.dto.response.LessonProgressResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.LessonBlockProgress;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LearningServiceImplTest {

    @Mock private CourseRepository courseRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private LessonBlockProgressRepository lessonBlockProgressRepository;
    @Mock private com.manabihub.learning.repository.FlashcardProgressRepository flashcardProgressRepository;
    @Mock private CurrentUserService currentUserService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @Mock private com.manabihub.writing.repository.WritingSubmissionRepository writingSubmissionRepository;
    @Mock private com.manabihub.writing.repository.AiWritingSuggestionRepository aiWritingSuggestionRepository;
    @Mock private com.manabihub.writing.repository.TeacherWritingFeedbackRepository teacherWritingFeedbackRepository;
    @Mock private com.manabihub.ai.repository.AiUsageLogRepository aiUsageLogRepository;
    @Mock private com.manabihub.ai.service.AiChatSettingsService aiChatSettingsService;
    @Mock private com.manabihub.ai.service.AiUsageLogService aiUsageLogService;
    @Mock private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Mock private com.manabihub.ai.provider.AiWritingAssistanceProvider aiWritingAssistanceProvider;
    @Mock private com.manabihub.learning.service.StudentAssessmentService studentAssessmentService;
    @Mock private com.manabihub.learning.service.CertificateEligibilityService certificateEligibilityService;
    @Mock private com.manabihub.notification.service.NotificationService notificationService;

    @InjectMocks
    private LearningServiceImpl learningService;

    private UUID currentUserId;
    private UUID studentId;
    private UUID courseId;
    private UUID moduleId;
    private UUID enrollmentId;
    private UUID blockVideoId;
    private UUID blockTextId;
    private UUID blockQuizId;

    private StudentProfile studentProfile;
    private Course course;
    private CourseModule courseModule;
    private Enrollment enrollment;
    private LessonBlock videoBlock;
    private LessonBlock textBlock;
    private LessonBlock quizBlock;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        moduleId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        blockVideoId = UUID.randomUUID();
        blockTextId = UUID.randomUUID();
        blockQuizId = UUID.randomUUID();

        studentProfile = StudentProfile.builder().id(studentId).build();

        courseModule = CourseModule.builder().id(moduleId).title("Module 1").orderIndex(1).blocks(new ArrayList<>()).build();
        course = Course.builder().id(courseId).title("Course").modules(new ArrayList<>()).build();
        course.addModule(courseModule);

        enrollment = Enrollment.builder().id(enrollmentId).student(studentProfile).course(course).status(EnrollmentStatus.ACTIVE).build();

        videoBlock = LessonBlock.builder().id(blockVideoId).type(LessonBlockType.VIDEO).title("Video 1")
                .videoUrl("https://cdn.example.com/video.mp4").durationMinutes(5).orderIndex(1).module(courseModule).build();
        textBlock = LessonBlock.builder().id(blockTextId).type(LessonBlockType.TEXT).title("Text 1")
                .content("Noi dung bai doc").orderIndex(2).module(courseModule).build();
        quizBlock = LessonBlock.builder().id(blockQuizId).type(LessonBlockType.QUIZ).title("Quiz 1")
                .quizQuestion("Cau hoi?").quizOptionsJson("[\"A\",\"B\"]").quizAnswer("A").orderIndex(3).module(courseModule).build();

        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<Object> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = inv.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private void mockActiveEnrollment() {
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(studentProfileRepository.findByUser_Id(currentUserId)).thenReturn(Optional.of(studentProfile));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(studentId, courseId)).thenReturn(Optional.of(enrollment));
        lenient().when(enrollmentRepository.findByIdForUpdate(enrollmentId)).thenReturn(Optional.of(enrollment));
    }

    private LessonBlockProgress completedProgress(LessonBlock block) {
        return LessonBlockProgress.builder().id(UUID.randomUUID()).enrollmentId(enrollment.getId()).lessonBlockId(block.getId())
                .status(LessonProgressStatus.COMPLETED).build();
    }

    // ==========================================
    // 1. openOrResumeCourse Tests (Order: 1xx)
    // ==========================================

    @Test
    @Order(101)
    @DisplayName("UTC01: Course NOT FOUND")
    void testOpenOrResumeCourse_UTC01_CourseNotFound() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.openOrResumeCourse(courseId));
        assertEquals(MessageCodes.COURSE_NOT_FOUND, ex.getMessageCode());
    }

    @Test
    @Order(102)
    @DisplayName("UTC02: Student profile NOT FOUND")
    void testOpenOrResumeCourse_UTC02_StudentProfileNotFound() {
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(studentProfileRepository.findByUser_Id(currentUserId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.openOrResumeCourse(courseId));
        assertEquals(MessageCodes.LEARNING_NOT_ENROLLED, ex.getMessageCode());
    }

    @Test
    @Order(103)
    @DisplayName("UTC03: Enrollment NOT FOUND (SRS 3a)")
    void testOpenOrResumeCourse_UTC03_EnrollmentNotFound() {
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(studentProfileRepository.findByUser_Id(currentUserId)).thenReturn(Optional.of(studentProfile));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(studentId, courseId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.openOrResumeCourse(courseId));
        assertEquals(MessageCodes.LEARNING_NOT_ENROLLED, ex.getMessageCode());
    }

    @Test
    @Order(104)
    @DisplayName("UTC04: Enrollment REVOKED - access denied (SRS 3a)")
    void testOpenOrResumeCourse_UTC04_EnrollmentRevoked() {
        enrollment.setStatus(EnrollmentStatus.REVOKED);
        mockActiveEnrollment();

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.openOrResumeCourse(courseId));
        assertEquals(MessageCodes.LEARNING_NOT_ENROLLED, ex.getMessageCode());
    }

    @Test
    @Order(105)
    @DisplayName("UTC05: Course has no lesson content (SRS 4b, non-fatal)")
    void testOpenOrResumeCourse_UTC05_NoContent() {
        mockActiveEnrollment();

        CourseLearningResponse response = learningService.openOrResumeCourse(courseId);
        assertTrue(response.modules().isEmpty());
        assertNull(response.currentLessonBlockId());
        assertEquals(0, response.totalLessons());
        assertFalse(response.warnings().isEmpty());
    }

    @Test
    @Order(106)
    @DisplayName("UTC06: First time studying - opens first lesson (SRS 4a)")
    void testOpenOrResumeCourse_UTC06_FirstLesson() {
        courseModule.addBlock(videoBlock);
        courseModule.addBlock(textBlock);
        courseModule.addBlock(quizBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId)).thenReturn(List.of());

        CourseLearningResponse response = learningService.openOrResumeCourse(courseId);
        assertEquals(blockVideoId, response.currentLessonBlockId());
        assertEquals(3, response.totalLessons());
        assertEquals(0, response.completedLessons());
        assertFalse(response.courseCompleted());
    }

    @Test
    @Order(107)
    @DisplayName("UTC07: Resume - continues from first incomplete lesson (SRS 5b)")
    void testOpenOrResumeCourse_UTC07_Resume() {
        courseModule.addBlock(videoBlock);
        courseModule.addBlock(textBlock);
        courseModule.addBlock(quizBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId)).thenReturn(List.of(completedProgress(videoBlock)));

        CourseLearningResponse response = learningService.openOrResumeCourse(courseId);
        assertEquals(blockTextId, response.currentLessonBlockId());
        assertEquals(1, response.completedLessons());
    }

    @Test
    @Order(108)
    @DisplayName("UTC08: All lessons completed")
    void testOpenOrResumeCourse_UTC08_AllCompleted() {
        courseModule.addBlock(videoBlock);
        courseModule.addBlock(textBlock);
        courseModule.addBlock(quizBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId))
                .thenReturn(List.of(completedProgress(videoBlock), completedProgress(textBlock), completedProgress(quizBlock)));

        CourseLearningResponse response = learningService.openOrResumeCourse(courseId);
        assertNull(response.currentLessonBlockId());
        assertTrue(response.courseCompleted());
        assertEquals(3, response.completedLessons());
    }

    @Test
    @Order(109)
    @DisplayName("UTC09: Lesson content missing - non-fatal warning (SRS 4b)")
    void testOpenOrResumeCourse_UTC09_ContentMissing() {
        videoBlock.setVideoUrl(null);
        courseModule.addBlock(videoBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId)).thenReturn(List.of());

        CourseLearningResponse response = learningService.openOrResumeCourse(courseId);
        assertFalse(response.warnings().isEmpty());
        LearningLessonBlockResponse blockResponse = response.modules().get(0).blocks().get(0);
        assertFalse(blockResponse.contentAvailable());
    }

    @Test
    @Order(110)
    @DisplayName("UTC10: Enrollment COMPLETED can still review the course")
    void testOpenOrResumeCourse_UTC10_CompletedEnrollmentStillHasAccess() {
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        courseModule.addBlock(videoBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId)).thenReturn(List.of(completedProgress(videoBlock)));

        CourseLearningResponse response = learningService.openOrResumeCourse(courseId);
        assertTrue(response.courseCompleted());
        assertEquals(1, response.completedLessons());
    }

    @Test
    @Order(111)
    @DisplayName("UTC11: Enrollment REFUNDED - access denied (SRS 3a)")
    void testOpenOrResumeCourse_UTC11_EnrollmentRefunded() {
        enrollment.setStatus(EnrollmentStatus.REFUNDED);
        mockActiveEnrollment();

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.openOrResumeCourse(courseId));
        assertEquals(MessageCodes.LEARNING_NOT_ENROLLED, ex.getMessageCode());
    }

    @Test
    @Order(112)
    @DisplayName("UTC12: Existing learner keeps access after course is removed from marketplace")
    void testOpenOrResumeCourse_UTC12_ForcedDraftPreservesExistingLearnerAccess() {
        course.setStatus(CourseStatus.FORCED_DRAFT);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        courseModule.addBlock(videoBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId)).thenReturn(List.of());

        CourseLearningResponse response = learningService.openOrResumeCourse(courseId);

        assertEquals(courseId, response.courseId());
        assertEquals(1, response.totalLessons());
        assertEquals(blockVideoId, response.currentLessonBlockId());
    }

    // ==========================================
    // 2. saveVideoProgress Tests (Order: 2xx)
    // ==========================================

    @Test
    @Order(201)
    @DisplayName("UTC01: Lesson block NOT FOUND")
    void testSaveVideoProgress_UTC01_BlockNotFound() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> learningService.saveVideoProgress(blockVideoId, new SaveVideoProgressRequest(10)));
        assertEquals(MessageCodes.CONTENT_NOT_FOUND, ex.getMessageCode());
    }

    @Test
    @Order(202)
    @DisplayName("UTC02: Not enrolled")
    void testSaveVideoProgress_UTC02_NotEnrolled() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(studentProfileRepository.findByUser_Id(currentUserId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> learningService.saveVideoProgress(blockVideoId, new SaveVideoProgressRequest(10)));
        assertEquals(MessageCodes.LEARNING_NOT_ENROLLED, ex.getMessageCode());
    }

    @Test
    @Order(203)
    @DisplayName("UTC03: Block type is not VIDEO")
    void testSaveVideoProgress_UTC03_InvalidBlockType() {
        when(lessonBlockRepository.findById(blockTextId)).thenReturn(Optional.of(textBlock));
        mockActiveEnrollment();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> learningService.saveVideoProgress(blockTextId, new SaveVideoProgressRequest(10)));
        assertEquals(MessageCodes.LEARNING_INVALID_BLOCK_TYPE, ex.getMessageCode());
    }

    @Test
    @Order(204)
    @DisplayName("UTC04: Video content missing (no videoUrl, SRS 4b)")
    void testSaveVideoProgress_UTC04_ContentMissing() {
        videoBlock.setVideoUrl(null);
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> learningService.saveVideoProgress(blockVideoId, new SaveVideoProgressRequest(10)));
        assertEquals(MessageCodes.LEARNING_LESSON_CONTENT_UNAVAILABLE, ex.getMessageCode());
    }

    @Test
    @Order(205)
    @DisplayName("UTC05: Video position and replay time are capped at duration")
    void testSaveVideoProgress_UTC05_CapsPositionAndReplayTime() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = learningService.saveVideoProgress(
                blockVideoId,
                new SaveVideoProgressRequest(400, 420)
        );

        assertEquals(300, response.lastVideoPositionSeconds());
        assertEquals(300, response.watchedVideoSeconds());
        assertEquals(LessonProgressStatus.COMPLETED, response.status());
    }

    @Test
    @Order(206)
    @DisplayName("UTC06: Create new progress (first save)")
    void testSaveVideoProgress_UTC06_CreateNew() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = learningService.saveVideoProgress(blockVideoId, new SaveVideoProgressRequest(120));
        assertEquals(LessonProgressStatus.IN_PROGRESS, response.status());
        assertEquals(120, response.lastVideoPositionSeconds());
    }

    @Test
    @DisplayName("saveVideoProgress auto-completes only after the watched duration is reached")
    void testSaveVideoProgress_AutoCompletesAtDuration() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = learningService.saveVideoProgress(
                blockVideoId,
                new SaveVideoProgressRequest(300, 300)
        );

        assertEquals(LessonProgressStatus.COMPLETED, response.status());
        assertEquals(300, response.watchedVideoSeconds());
        assertNotNull(response.completedAt());
    }

    @Test
    @DisplayName("YouTube/upload player duration can complete a lesson whose editorial duration is only an estimate")
    void testSaveVideoProgress_UsesObservedMediaDuration() {
        videoBlock.setDurationMinutes(12);
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = learningService.saveVideoProgress(
                blockVideoId,
                new SaveVideoProgressRequest(214, 214, 214)
        );

        assertEquals(LessonProgressStatus.COMPLETED, response.status());
        verify(lessonBlockProgressRepository).save(argThat(progress -> progress.getVideoDurationSeconds() == 214));
    }

    @Test
    @Order(207)
    @DisplayName("UTC07: Resume video - update existing IN_PROGRESS (SRS 5b)")
    void testSaveVideoProgress_UTC07_UpdateInProgress() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        LessonBlockProgress existing = LessonBlockProgress.builder().id(UUID.randomUUID()).enrollmentId(enrollment.getId()).lessonBlockId(videoBlock.getId())
                .status(LessonProgressStatus.IN_PROGRESS).lastVideoPositionSeconds(60).build();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.of(existing));
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = learningService.saveVideoProgress(blockVideoId, new SaveVideoProgressRequest(200));
        assertEquals(LessonProgressStatus.IN_PROGRESS, response.status());
        assertEquals(200, response.lastVideoPositionSeconds());
    }

    @Test
    @Order(208)
    @DisplayName("UTC08: Update position on already COMPLETED lesson - status unchanged")
    void testSaveVideoProgress_UTC08_CompletedStaysCompleted() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        Instant completedAt = Instant.now().minusSeconds(3600);
        LessonBlockProgress existing = LessonBlockProgress.builder().id(UUID.randomUUID()).enrollmentId(enrollment.getId()).lessonBlockId(videoBlock.getId())
                .status(LessonProgressStatus.COMPLETED).completedAt(completedAt).lastVideoPositionSeconds(300).build();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.of(existing));
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = learningService.saveVideoProgress(blockVideoId, new SaveVideoProgressRequest(100));
        assertEquals(LessonProgressStatus.COMPLETED, response.status());
        assertEquals(100, response.lastVideoPositionSeconds());
        assertEquals(completedAt, response.completedAt());
    }

    // ==========================================
    // 3. markLessonComplete Tests (Order: 3xx)
    // ==========================================

    @Test
    @Order(301)
    @DisplayName("UTC01: Lesson block NOT FOUND")
    void testMarkLessonComplete_UTC01_BlockNotFound() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.markLessonComplete(blockVideoId));
        assertEquals(MessageCodes.CONTENT_NOT_FOUND, ex.getMessageCode());
    }

    @Test
    @Order(302)
    @DisplayName("UTC02: Not enrolled")
    void testMarkLessonComplete_UTC02_NotEnrolled() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(studentProfileRepository.findByUser_Id(currentUserId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.markLessonComplete(blockVideoId));
        assertEquals(MessageCodes.LEARNING_NOT_ENROLLED, ex.getMessageCode());
    }

    @Test
    @Order(303)
    @DisplayName("UTC03: Reject video completion before the full duration was watched")
    void testMarkLessonComplete_UTC03_CreateNew() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> learningService.markLessonComplete(blockVideoId));
        assertEquals(MessageCodes.COMMON_BAD_REQUEST, exception.getMessageCode());
    }

    @Test
    @Order(304)
    @DisplayName("UTC04: Complete from IN_PROGRESS")
    void testMarkLessonComplete_UTC04_FromInProgress() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        LessonBlockProgress existing = LessonBlockProgress.builder().id(UUID.randomUUID()).enrollmentId(enrollment.getId()).lessonBlockId(videoBlock.getId())
                .status(LessonProgressStatus.IN_PROGRESS).watchedVideoSeconds(300).build();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.of(existing));
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = learningService.markLessonComplete(blockVideoId);
        assertEquals(LessonProgressStatus.COMPLETED, response.status());
        assertNotNull(response.completedAt());
    }

    @Test
    @Order(305)
    @DisplayName("UTC05: Complete again - idempotent, completedAt unchanged")
    void testMarkLessonComplete_UTC05_Idempotent() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        Instant firstCompletedAt = Instant.now().minusSeconds(600);
        LessonBlockProgress existing = LessonBlockProgress.builder().id(UUID.randomUUID()).enrollmentId(enrollment.getId()).lessonBlockId(videoBlock.getId())
                .status(LessonProgressStatus.COMPLETED).completedAt(firstCompletedAt).build();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.of(existing));
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = learningService.markLessonComplete(blockVideoId);
        assertEquals(firstCompletedAt, response.completedAt());
    }

    @ParameterizedTest
    @EnumSource(value = LessonBlockType.class, names = {"QUIZ", "FLASHCARD", "WRITING"})
    @Order(306)
    @DisplayName("UTC06: Reject completion for interactive blocks (QUIZ, FLASHCARD, WRITING)")
    void testMarkLessonComplete_UTC06_RejectInteractive(LessonBlockType blockType) {
        LessonBlock interactiveBlock = LessonBlock.builder().id(UUID.randomUUID()).type(blockType).module(courseModule).build();
        when(lessonBlockRepository.findById(interactiveBlock.getId())).thenReturn(Optional.of(interactiveBlock));

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            learningService.markLessonComplete(interactiveBlock.getId());
        });
        assertEquals(MessageCodes.COMMON_BAD_REQUEST, ex.getMessageCode());
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @Order(307)
    @DisplayName("UTC07: Completing the last remaining lesson marks enrollment COMPLETED (SRS 8-9)")
    void testMarkLessonComplete_UTC07_LastLessonCompletesEnrollment() {
        courseModule.addBlock(videoBlock);
        courseModule.addBlock(textBlock);
        when(lessonBlockRepository.findById(blockTextId)).thenReturn(Optional.of(textBlock));
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId)).thenReturn(List.of(completedProgress(videoBlock)));
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockTextId)).thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // removed unnecessary stubbing

        LessonProgressResponse response = learningService.markLessonComplete(blockTextId);

        assertEquals(LessonProgressStatus.COMPLETED, response.status());
        assertEquals(EnrollmentStatus.ACTIVE, enrollment.getStatus());
        assertNull(enrollment.getCompletedAt());

    }

    @Test
    @Order(308)
    @DisplayName("UTC08: Completing one lesson while others remain keeps enrollment ACTIVE")
    void testMarkLessonComplete_UTC08_RemainingLessonsKeepEnrollmentActive() {
        courseModule.addBlock(videoBlock);
        courseModule.addBlock(textBlock);
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, blockVideoId)).thenReturn(Optional.of(
                LessonBlockProgress.builder().id(UUID.randomUUID()).enrollmentId(enrollmentId).lessonBlockId(blockVideoId)
                        .status(LessonProgressStatus.IN_PROGRESS).watchedVideoSeconds(300).build()));
        when(lessonBlockProgressRepository.save(any(LessonBlockProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // removed unnecessary stubbing

        LessonProgressResponse response = learningService.markLessonComplete(blockVideoId);

        assertEquals(LessonProgressStatus.COMPLETED, response.status());
        assertEquals(EnrollmentStatus.ACTIVE, enrollment.getStatus());
        assertNull(enrollment.getCompletedAt());
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    // ==========================================
    // 4. getCourseProgress Tests (Order: 4xx)
    // ==========================================

    @Test
    @Order(401)
    @DisplayName("UTC01: Course NOT FOUND")
    void testGetCourseProgress_UTC01_CourseNotFound() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.getCourseProgress(courseId));
        assertEquals(MessageCodes.COURSE_NOT_FOUND, ex.getMessageCode());
    }

    @Test
    @Order(402)
    @DisplayName("UTC02: Not enrolled")
    void testGetCourseProgress_UTC02_NotEnrolled() {
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(studentProfileRepository.findByUser_Id(currentUserId)).thenReturn(Optional.of(studentProfile));
        when(enrollmentRepository.findByStudent_IdAndCourse_Id(studentId, courseId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> learningService.getCourseProgress(courseId));
        assertEquals(MessageCodes.LEARNING_NOT_ENROLLED, ex.getMessageCode());
    }

    @Test
    @Order(403)
    @DisplayName("UTC03: Course has no lessons")
    void testGetCourseProgress_UTC03_NoLessons() {
        mockActiveEnrollment();

        CourseProgressSummaryResponse response = learningService.getCourseProgress(courseId);
        assertEquals(0, response.totalLessons());
        assertEquals(0, response.completedLessons());
        assertEquals(0.0, response.progressPercent());
        assertNull(response.nextLessonBlockId());
    }

    @Test
    @Order(404)
    @DisplayName("UTC04: No lesson completed yet")
    void testGetCourseProgress_UTC04_NoneCompleted() {
        courseModule.addBlock(videoBlock);
        courseModule.addBlock(textBlock);
        courseModule.addBlock(quizBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId)).thenReturn(List.of());

        CourseProgressSummaryResponse response = learningService.getCourseProgress(courseId);
        assertEquals(3, response.totalLessons());
        assertEquals(0, response.completedLessons());
        assertEquals(blockVideoId, response.nextLessonBlockId());
    }

    @Test
    @Order(405)
    @DisplayName("UTC05: Partial completion")
    void testGetCourseProgress_UTC05_PartialCompletion() {
        courseModule.addBlock(videoBlock);
        courseModule.addBlock(textBlock);
        courseModule.addBlock(quizBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId))
                .thenReturn(List.of(completedProgress(videoBlock), completedProgress(quizBlock)));

        CourseProgressSummaryResponse response = learningService.getCourseProgress(courseId);
        assertEquals(2, response.completedLessons());
        assertEquals(66.67, response.progressPercent(), 0.01);
        assertEquals(blockTextId, response.nextLessonBlockId());
        assertFalse(response.courseCompleted());
    }

    @Test
    @Order(406)
    @DisplayName("UTC06: All lessons completed - 100%")
    void testGetCourseProgress_UTC06_AllCompleted() {
        courseModule.addBlock(videoBlock);
        courseModule.addBlock(textBlock);
        courseModule.addBlock(quizBlock);
        mockActiveEnrollment();
        when(lessonBlockProgressRepository.findByEnrollmentId(enrollmentId))
                .thenReturn(List.of(completedProgress(videoBlock), completedProgress(textBlock), completedProgress(quizBlock)));

        CourseProgressSummaryResponse response = learningService.getCourseProgress(courseId);
        assertEquals(100.0, response.progressPercent());
        assertNull(response.nextLessonBlockId());
        assertTrue(response.courseCompleted());
    }

    // ==========================================
    // 5. reviewFlashcard Tests
    // ==========================================

    @Test
    @DisplayName("Review Flashcard - Wrong Type")
    void testReviewFlashcard_WrongType() {
        mockActiveEnrollment();
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));

        com.manabihub.learning.dto.request.ReviewFlashcardRequest request = new com.manabihub.learning.dto.request.ReviewFlashcardRequest(0, com.manabihub.learning.enums.FlashcardStatus.REMEMBERED);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                learningService.reviewFlashcard(blockVideoId, request));

        assertEquals(MessageCodes.LEARNING_INVALID_BLOCK_TYPE, exception.getMessageCode());
    }

    @Test
    @DisplayName("Review Flashcard - Invalid Index")
    void testReviewFlashcard_InvalidIndex() {
        mockActiveEnrollment();
        LessonBlock fb = LessonBlock.builder().id(UUID.randomUUID()).type(LessonBlockType.FLASHCARD)
                .flashcardsJson("[{\"front\":\"A\",\"back\":\"B\"}]").module(courseModule).build();
        when(lessonBlockRepository.findById(fb.getId())).thenReturn(Optional.of(fb));

        com.manabihub.learning.dto.request.ReviewFlashcardRequest request = new com.manabihub.learning.dto.request.ReviewFlashcardRequest(1, com.manabihub.learning.enums.FlashcardStatus.REMEMBERED);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                learningService.reviewFlashcard(fb.getId(), request));

        assertEquals(MessageCodes.LEARNING_INVALID_FLASHCARD_INDEX, exception.getMessageCode());
    }

    @Test
    @DisplayName("Review Flashcard - Success - In Progress")
    void testReviewFlashcard_Success_InProgress() {
        mockActiveEnrollment();
        LessonBlock fb = LessonBlock.builder().id(UUID.randomUUID()).type(LessonBlockType.FLASHCARD)
                .flashcardsJson("[{\"front\":\"A\",\"back\":\"B\"}, {\"front\":\"C\",\"back\":\"D\"}]").module(courseModule).build();
        when(lessonBlockRepository.findById(fb.getId())).thenReturn(Optional.of(fb));
        when(flashcardProgressRepository.countByEnrollmentIdAndLessonBlockId(enrollmentId, fb.getId())).thenReturn(1);
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, fb.getId())).thenReturn(Optional.empty());
        when(lessonBlockProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.manabihub.learning.dto.request.ReviewFlashcardRequest request = new com.manabihub.learning.dto.request.ReviewFlashcardRequest(0, com.manabihub.learning.enums.FlashcardStatus.REMEMBERED);
        LessonProgressResponse response = learningService.reviewFlashcard(fb.getId(), request);

        assertEquals(LessonProgressStatus.IN_PROGRESS, response.status());
        assertNull(response.completedAt());
        verify(flashcardProgressRepository).upsertStatus(enrollmentId, fb.getId(), 0, com.manabihub.learning.enums.FlashcardStatus.REMEMBERED);
    }

    @Test
    @DisplayName("Review Flashcard - Success - Completed")
    void testReviewFlashcard_Success_Completed() {
        mockActiveEnrollment();
        LessonBlock fb = LessonBlock.builder().id(UUID.randomUUID()).type(LessonBlockType.FLASHCARD)
                .flashcardsJson("[{\"front\":\"A\",\"back\":\"B\"}, {\"front\":\"C\",\"back\":\"D\"}]").module(courseModule).build();
        when(lessonBlockRepository.findById(fb.getId())).thenReturn(Optional.of(fb));
        when(flashcardProgressRepository.countByEnrollmentIdAndLessonBlockId(enrollmentId, fb.getId())).thenReturn(2);
        LessonBlockProgress existingProgress = LessonBlockProgress.builder().id(UUID.randomUUID())
                .enrollmentId(enrollmentId).lessonBlockId(fb.getId()).status(LessonProgressStatus.IN_PROGRESS).build();
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, fb.getId())).thenReturn(Optional.of(existingProgress));
        when(lessonBlockProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.manabihub.learning.dto.request.ReviewFlashcardRequest request = new com.manabihub.learning.dto.request.ReviewFlashcardRequest(1, com.manabihub.learning.enums.FlashcardStatus.NEEDS_REVIEW);
        LessonProgressResponse response = learningService.reviewFlashcard(fb.getId(), request);

        assertEquals(LessonProgressStatus.COMPLETED, response.status());
        assertNotNull(response.completedAt());
        verify(flashcardProgressRepository).upsertStatus(enrollmentId, fb.getId(), 1, com.manabihub.learning.enums.FlashcardStatus.NEEDS_REVIEW);
    }

    // --- Writing Assignment & AI Tests ---

    @Test
    @DisplayName("saveWritingDraft creates a DRAFT without completing the lesson")
    void testSaveWritingDraftCreatesDraft() {
        UUID writingBlockId = UUID.randomUUID();
        LessonBlock writingBlock = LessonBlock.builder()
                .id(writingBlockId)
                .type(LessonBlockType.WRITING)
                .title("Writing")
                .module(courseModule)
                .build();

        when(lessonBlockRepository.findById(writingBlockId)).thenReturn(Optional.of(writingBlock));
        mockActiveEnrollment();
        when(writingSubmissionRepository.findByEnrollmentIdAndLessonBlockIdForUpdate(enrollmentId, writingBlockId))
                .thenReturn(Optional.empty());
        when(writingSubmissionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = learningService.saveWritingDraft(
                writingBlockId,
                new com.manabihub.writing.dto.request.WritingDraftRequest("draft content"));

        assertEquals(com.manabihub.writing.enums.WritingSubmissionStatus.DRAFT, response.status());
        assertEquals("draft content", response.content());
        verify(lessonBlockProgressRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitWriting promotes an existing DRAFT instead of inserting a duplicate row")
    void testSubmitWritingPromotesExistingDraft() {
        UUID writingBlockId = UUID.randomUUID();
        LessonBlock writingBlock = LessonBlock.builder()
                .id(writingBlockId)
                .type(LessonBlockType.WRITING)
                .title("Writing")
                .module(courseModule)
                .build();
        var draft = com.manabihub.writing.entity.WritingSubmission.builder()
                .id(UUID.randomUUID())
                .enrollment(enrollment)
                .student(studentProfile)
                .lessonBlockId(writingBlockId)
                .content("old draft")
                .status(com.manabihub.writing.enums.WritingSubmissionStatus.DRAFT)
                .submittedAt(Instant.now())
                .build();

        when(lessonBlockRepository.findById(writingBlockId)).thenReturn(Optional.of(writingBlock));
        mockActiveEnrollment();
        when(writingSubmissionRepository.findByEnrollmentIdAndLessonBlockIdForUpdate(enrollmentId, writingBlockId))
                .thenReturn(Optional.of(draft));
        when(writingSubmissionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = learningService.submitWriting(
                writingBlockId,
                new com.manabihub.writing.dto.request.WritingSubmissionRequest("final content"));

        assertEquals(com.manabihub.writing.enums.WritingSubmissionStatus.SUBMITTED, response.status());
        assertEquals("final content", response.content());
        verify(writingSubmissionRepository).saveAndFlush(draft);
        verify(lessonBlockProgressRepository).save(any());
    }

    @Test
    @DisplayName("getWritingSubmission returns distinct non-official AI suggestion and official teacher feedback")
    void testGetWritingSubmission_ReturnsBothFeedbackSources() {
        UUID writingBlockId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        LessonBlock writingBlock = LessonBlock.builder()
                .id(writingBlockId)
                .type(LessonBlockType.WRITING)
                .title("Writing")
                .module(courseModule)
                .build();
        com.manabihub.writing.entity.WritingSubmission submission =
                com.manabihub.writing.entity.WritingSubmission.builder()
                        .id(submissionId)
                        .enrollment(enrollment)
                        .lessonBlockId(writingBlockId)
                        .content("My essay")
                        .status(com.manabihub.writing.enums.WritingSubmissionStatus.TEACHER_FEEDBACK_READY)
                        .submittedAt(Instant.now())
                        .build();
        com.manabihub.writing.entity.AiWritingSuggestion suggestion =
                com.manabihub.writing.entity.AiWritingSuggestion.builder()
                        .id(UUID.randomUUID())
                        .writingSubmission(submission)
                        .status("READY")
                        .official(false)
                        .createdAt(Instant.now())
                        .build();
        com.manabihub.writing.entity.TeacherWritingFeedback feedback =
                com.manabihub.writing.entity.TeacherWritingFeedback.builder()
                        .id(UUID.randomUUID())
                        .writingSubmission(submission)
                        .score(new java.math.BigDecimal("8.50"))
                        .comment("Good revision.")
                        .official(true)
                        .createdAt(Instant.now())
                        .build();

        when(lessonBlockRepository.findById(writingBlockId)).thenReturn(Optional.of(writingBlock));
        mockActiveEnrollment();
        when(writingSubmissionRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, writingBlockId))
                .thenReturn(Optional.of(submission));
        when(aiWritingSuggestionRepository.findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submissionId))
                .thenReturn(Optional.of(suggestion));
        when(teacherWritingFeedbackRepository.findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submissionId))
                .thenReturn(Optional.of(feedback));

        var response = learningService.getWritingSubmission(writingBlockId);

        assertNotNull(response.aiSuggestion());
        assertFalse(response.aiSuggestion().official());
        assertNotNull(response.teacherFeedback());
        assertTrue(response.teacherFeedback().official());
        assertEquals("Good revision.", response.teacherFeedback().comment());
        assertEquals(0, response.teacherFeedback().score().compareTo(new java.math.BigDecimal("8.50")));
    }

    @Test
    @DisplayName("getWritingSubmission scopes lookup to current student's enrollment")
    void testGetWritingSubmission_UsesCurrentEnrollmentOnly() {
        UUID writingBlockId = UUID.randomUUID();
        LessonBlock writingBlock = LessonBlock.builder()
                .id(writingBlockId)
                .type(LessonBlockType.WRITING)
                .title("Writing")
                .module(courseModule)
                .build();

        when(lessonBlockRepository.findById(writingBlockId)).thenReturn(Optional.of(writingBlock));
        mockActiveEnrollment();
        when(writingSubmissionRepository.findByEnrollmentIdAndLessonBlockId(enrollmentId, writingBlockId))
                .thenReturn(Optional.empty());

        assertNull(learningService.getWritingSubmission(writingBlockId));
        verify(writingSubmissionRepository)
                .findByEnrollmentIdAndLessonBlockId(enrollmentId, writingBlockId);
    }

    @Test
    @DisplayName("requestAiWritingAssistance - Forbidden - Another student's submission")
    void testRequestAiWriting_Forbidden_OtherStudent() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        UUID subId = UUID.randomUUID();
        com.manabihub.writing.entity.WritingSubmission sub = com.manabihub.writing.entity.WritingSubmission.builder()
                .enrollment(Enrollment.builder().id(UUID.randomUUID()).build())
                .lessonBlockId(blockVideoId)
                .build();
        when(writingSubmissionRepository.findByIdAndEnrollmentIdAndLessonBlockId(subId, enrollment.getId(), blockVideoId)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () ->
                learningService.requestAiWritingAssistance(blockVideoId, subId));
        assertEquals(MessageCodes.COMMON_NOT_FOUND, ex.getMessageCode());
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("requestAiWritingAssistance - Rate Limit - Minute limit exceeded")
    void testRequestAiWriting_MinuteRateLimit() {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        UUID subId = UUID.randomUUID();
        com.manabihub.writing.entity.WritingSubmission sub = com.manabihub.writing.entity.WritingSubmission.builder()
                .enrollment(enrollment).lessonBlockId(blockVideoId).status(com.manabihub.writing.enums.WritingSubmissionStatus.SUBMITTED).build();
        when(writingSubmissionRepository.findByIdAndEnrollmentIdAndLessonBlockId(subId, enrollment.getId(), blockVideoId)).thenReturn(Optional.of(sub));

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);

        com.manabihub.ai.service.AiChatSettingsService.AiChatSettings settings = new com.manabihub.ai.service.AiChatSettingsService.AiChatSettings(
                true, true, true, new java.math.BigDecimal("100000"), 5, 20);
        when(aiChatSettingsService.getSettings()).thenReturn(settings);
        course.setPrice(new java.math.BigDecimal("200000"));
        course.setAiSupported(true);

        when(aiUsageLogRepository.countByUserIdAndFeatureCodeAndRequestStatusAndCreatedAtAfter(
                eq(currentUserId), eq("AI_WRITING_ASSISTANCE"), eq(com.manabihub.ai.enums.AiUsageRequestStatus.SUCCESS), any(Instant.class)
        )).thenReturn(5L); // Reached minute limit

        BusinessException ex = assertThrows(BusinessException.class, () ->
                learningService.requestAiWritingAssistance(blockVideoId, subId));
        assertEquals(MessageCodes.MSG_AI_001, ex.getMessageCode());
    }

    @Test
    @DisplayName("requestAiWritingAssistance - Success")
    void testRequestAiWriting_Success() throws Exception {
        when(lessonBlockRepository.findById(blockVideoId)).thenReturn(Optional.of(videoBlock));
        mockActiveEnrollment();
        UUID subId = UUID.randomUUID();
        com.manabihub.writing.entity.WritingSubmission sub = com.manabihub.writing.entity.WritingSubmission.builder()
                .id(subId).enrollment(enrollment).lessonBlockId(blockVideoId).content("Hello")
                .status(com.manabihub.writing.enums.WritingSubmissionStatus.SUBMITTED)
                .submittedAt(Instant.now()).build();
        when(writingSubmissionRepository.findByIdAndEnrollmentIdAndLessonBlockId(subId, enrollment.getId(), blockVideoId)).thenReturn(Optional.of(sub));
        when(writingSubmissionRepository.findByIdAndEnrollmentIdAndLessonBlockIdForUpdate(subId, enrollment.getId(), blockVideoId)).thenReturn(Optional.of(sub));
        when(writingSubmissionRepository.findById(subId)).thenReturn(Optional.of(sub));
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);

        com.manabihub.ai.service.AiChatSettingsService.AiChatSettings settings = new com.manabihub.ai.service.AiChatSettingsService.AiChatSettings(
                true, true, true, new java.math.BigDecimal("100000"), 5, 20);
        when(aiChatSettingsService.getSettings()).thenReturn(settings);
        course.setPrice(new java.math.BigDecimal("200000"));
        course.setAiSupported(true);

        when(aiUsageLogRepository.countByUserIdAndFeatureCodeAndRequestStatusAndCreatedAtAfter(
                any(), any(), any(), any()
        )).thenReturn(0L);

        com.fasterxml.jackson.databind.JsonNode mockNode = objectMapper.createArrayNode();
        com.manabihub.ai.provider.AiWritingAssistanceProvider.Result mockResult = new com.manabihub.ai.provider.AiWritingAssistanceProvider.Result(
                mockNode, mockNode, mockNode, "Good job", "provider", 10, 10
        );
        when(aiWritingAssistanceProvider.generate(any(), any(), any())).thenReturn(mockResult);

        com.manabihub.writing.dto.response.StudentWritingSubmissionResponse res = learningService.requestAiWritingAssistance(blockVideoId, subId);

        verify(aiWritingAssistanceProvider).generate(any(), any(), eq("Hello"));
        verify(writingSubmissionRepository, atLeastOnce()).save(any());
        verify(aiWritingSuggestionRepository, atLeastOnce()).save(any());
        verify(aiUsageLogService).record(any(), any(), any(), any(), any(), eq(com.manabihub.ai.enums.AiUsageRequestStatus.SUCCESS), any(), any(), any(), any());
    }

    @Test
    @DisplayName("submitWriting - notifies the course teacher with the submission link")
    void submitWriting_notifiesTeacher() {
        UUID writingBlockId = UUID.randomUUID();
        LessonBlock writingBlock = LessonBlock.builder()
                .id(writingBlockId)
                .type(LessonBlockType.WRITING)
                .title("Viết đoạn giới thiệu bản thân")
                .orderIndex(4)
                .module(courseModule)
                .build();
        com.manabihub.kyc.domain.AppUser teacherUser = new com.manabihub.kyc.domain.AppUser();
        teacherUser.setId(UUID.randomUUID());
        teacherUser.setEmail("teacher@example.test");
        com.manabihub.kyc.domain.TeacherProfile teacher = new com.manabihub.kyc.domain.TeacherProfile();
        teacher.setId(UUID.randomUUID());
        teacher.setUser(teacherUser);
        course.setTeacher(teacher);
        studentProfile.setDisplayName("Học viên An");

        when(lessonBlockRepository.findById(writingBlockId)).thenReturn(Optional.of(writingBlock));
        mockActiveEnrollment();
        when(writingSubmissionRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    com.manabihub.writing.entity.WritingSubmission submission = invocation.getArgument(0);
                    submission.setId(UUID.randomUUID());
                    return submission;
                });
        when(lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(
                enrollment.getId(), writingBlockId
        )).thenReturn(Optional.empty());

        com.manabihub.writing.dto.response.StudentWritingSubmissionResponse response =
                learningService.submitWriting(
                        writingBlockId,
                        new com.manabihub.writing.dto.request.WritingSubmissionRequest(
                                "Đây là bài viết hoàn chỉnh của học viên."
                        )
                );

        verify(notificationService).createNotification(
                eq(teacherUser.getId()),
                eq(teacherUser.getEmail()),
                eq("Có bài viết mới cần phản hồi"),
                contains("Viết đoạn giới thiệu bản thân"),
                eq(com.manabihub.notification.NotificationTypes.WRITING_SUBMITTED),
                eq("/teacher/writing-reviews/" + response.id())
        );
    }
}
