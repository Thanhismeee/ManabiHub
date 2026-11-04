package com.manabihub.course.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.ai.service.AiCourseEligibilityService;
import com.manabihub.course.dto.request.CreateCourseDraftRequest;
import com.manabihub.course.dto.response.CourseDraftResponse;
import com.manabihub.course.dto.response.ValidationResultResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.repository.CourseCategoryRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.service.CourseValidationService;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.kyc.service.TeacherQualificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import com.manabihub.audit.service.AuditLogService;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.review.service.CourseReviewService;
import com.manabihub.systemconfig.service.SystemSettingValueService;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.wallet.repository.EscrowLedgerRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseCategoryRepository courseCategoryRepository;

    @Mock
    private TeacherProfileRepository teacherProfileRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private CourseValidationService courseValidationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CourseReviewService courseReviewService;

    @Mock
    private SystemSettingValueService settingValueService;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private EscrowLedgerRepository escrowLedgerRepository;

    @Mock
    private CourseEditDraftService courseEditDraftService;

    @Mock
    private AiCourseEligibilityService aiCourseEligibilityService;

    @Mock
    private TeacherQualificationService teacherQualificationService;

    @InjectMocks
    private CourseServiceImpl courseService;
    private UUID userId;
    private TeacherProfile approvedTeacher;

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(
                courseRepository,
                courseCategoryRepository,
                teacherProfileRepository,
                currentUserService,
                courseValidationService,
                auditLogService,
                notificationService,
                courseReviewService,
                settingValueService,
                enrollmentRepository,
                escrowLedgerRepository,
                courseEditDraftService,
                aiCourseEligibilityService,
                teacherQualificationService
        );
        org.mockito.Mockito.lenient()
                .when(courseEditDraftService.resolveEditableCourse(any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(settingValueService.getInteger(any(String.class), any(Integer.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        org.mockito.Mockito.lenient()
                .when(settingValueService.getDecimal(any(String.class), any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        userId = UUID.randomUUID();
        approvedTeacher = new TeacherProfile();
        approvedTeacher.setId(UUID.randomUUID());
        approvedTeacher.setKycStatus(TeacherKycStatus.APPROVED);
        approvedTeacher.setCanPublishCourse(true);
    }

    // Sheets 14-17 (createDraft / updateDraft / listMyDrafts / deleteDraft) — các đợt trước.
    // Bọc @Nested để lớp ngoài không còn test nào, nếu không Surefire sẽ gộp chúng vào nhóm cuối.

    @Nested
    @DisplayName("(dot truoc) - createDraft / updateDraft / listMyDrafts / deleteDraft")
    class DraftManagement {

    @Test
    void createDraft_WhenTeacherApproved_ShouldSaveDraftWithLearningGoals() {
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseCategoryRepository.existsByCodeAndActiveTrue("GRAMMAR")).thenReturn(true);
        when(courseRepository.existsBySlug("jlpt-n5-foundation")).thenReturn(false);
        CreateCourseDraftRequest request = validRequest();
        when(aiCourseEligibilityService.isPriceEligible(request.price())).thenReturn(true);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(UUID.randomUUID());
            return course;
        });

        CourseDraftResponse response = courseService.createDraft(request);

        assertNotNull(response.id());
        assertEquals(CourseStatus.DRAFT, response.status());
        assertEquals("jlpt-n5-foundation", response.slug());
        assertEquals(4, response.learningGoals().size());
        assertEquals("UC-23", response.srsTrace().get("uc"));
        assertTrue(response.srsTrace().toString().contains("BR-GOAL-01"));
        assertTrue(response.srsTrace().toString().contains("BR-COURSE-04"));
        assertTrue(response.srsTrace().toString().contains(MessageCodes.MSG_COURSE_004));

        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(courseCaptor.capture());

        Course savedCourse = courseCaptor.getValue();
        assertEquals(CourseStatus.DRAFT, savedCourse.getStatus());
        assertEquals(approvedTeacher.getId(), savedCourse.getTeacher().getId());
        assertEquals(4, savedCourse.getLearningGoals().size());
        assertTrue(savedCourse.isAiSupported());
        assertTrue(savedCourse.getLearningGoals().stream().allMatch(goal -> goal.getGoalText().length() <= 160));
    }

    @Test
    void createDraft_WhenTitleIsBlank_ShouldAssignDefaultDraftTitle() {
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseCategoryRepository.existsByCodeAndActiveTrue("GRAMMAR")).thenReturn(true);
        when(courseRepository.existsBySlug(any())).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(UUID.randomUUID());
            return course;
        });

        CreateCourseDraftRequest request = new CreateCourseDraftRequest(
                "",
                "Introductory Japanese course for new learners.",
                JlptLevel.N5,
                "GRAMMAR",
                null,
                "Learners can understand basic N5 grammar and vocabulary.",
                BigDecimal.ZERO,
                "No prerequisites",
                "Students starting Japanese from zero",
                List.of(
                        "Read Hiragana and Katakana with confidence",
                        "Understand core N5 sentence patterns",
                        "Use basic greetings and classroom phrases",
                        "Prepare for beginner JLPT N5 practice"
                )
        );

        CourseDraftResponse response = courseService.createDraft(request);

        assertTrue(response.title().startsWith("[Bản nháp] Khóa học chưa đặt tên - "));
        assertTrue(response.slug().startsWith("ban-nhap-khoa-hoc-chua-dat-ten"));
    }

    @Test
    void listMyDrafts_WhenTeacherApproved_ShouldReturnDraftCourses() {
        Course draft = Course.builder()
                .id(UUID.randomUUID())
                .teacher(approvedTeacher)
                .title("JLPT N5 Foundation")
                .slug("jlpt-n5-foundation")
                .introduction("Introductory Japanese course for new learners.")
                .jlptLevel(JlptLevel.N5)
                .category("GRAMMAR")
                .outcomes("Learners can understand basic N5 grammar and vocabulary.")
                .price(BigDecimal.valueOf(100000))
                .currency("VND")
                .prerequisites("No prerequisites")
                .targetStudents("Students starting Japanese from zero")
                .status(CourseStatus.DRAFT)
                .build();
        draft.addLearningGoal("Read Hiragana and Katakana with confidence", 1);

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByTeacher_IdAndStatusOrderByCreatedAtDesc(approvedTeacher.getId(), CourseStatus.DRAFT))
                .thenReturn(List.of(draft));

        List<CourseDraftResponse> responses = courseService.listMyDrafts();

        assertEquals(1, responses.size());
        assertEquals(draft.getId(), responses.get(0).id());
        assertEquals(CourseStatus.DRAFT, responses.get(0).status());
    }

    @Test
    void listMyCourses_WhenTeacherApproved_ShouldIncludeApprovedAndPublishedCourses() {
        Course approvedCourse = Course.builder()
                .id(UUID.randomUUID())
                .teacher(approvedTeacher)
                .title("Approved course")
                .slug("approved-course")
                .status(CourseStatus.APPROVED)
                .build();
        Course publishedCourse = Course.builder()
                .id(UUID.randomUUID())
                .teacher(approvedTeacher)
                .title("Published course")
                .slug("published-course")
                .status(CourseStatus.PUBLISHED)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByTeacher_IdAndStatusNotOrderByCreatedAtDesc(
                approvedTeacher.getId(),
                CourseStatus.ARCHIVED
        )).thenReturn(List.of(approvedCourse, publishedCourse));

        List<CourseDraftResponse> responses = courseService.listMyCourses();

        assertEquals(2, responses.size());
        assertEquals(CourseStatus.APPROVED, responses.get(0).status());
        assertEquals(CourseStatus.PUBLISHED, responses.get(1).status());
    }

    @Test
    void updateDraft_WhenDraftExists_ShouldUpdateFieldsAndGoals() {
        UUID draftId = UUID.randomUUID();
        Course draft = Course.builder()
                .id(draftId)
                .teacher(approvedTeacher)
                .title("Old title")
                .slug("old-title")
                .introduction("Old introduction")
                .jlptLevel(JlptLevel.N5)
                .category("GRAMMAR")
                .outcomes("Old outcomes")
                .price(BigDecimal.ZERO)
                .currency("VND")
                .prerequisites("Old prerequisites")
                .targetStudents("Old students")
                .status(CourseStatus.DRAFT)
                .build();
        draft.addLearningGoal("Old goal", 1);

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_IdAndStatusIn(
                draftId,
                approvedTeacher.getId(),
                List.of(CourseStatus.DRAFT, CourseStatus.REJECTED, CourseStatus.FORCED_DRAFT)
        ))
                .thenReturn(Optional.of(draft));
        when(courseCategoryRepository.existsByCodeAndActiveTrue("GRAMMAR")).thenReturn(true);
        when(courseRepository.existsBySlugAndIdNot("jlpt-n5-foundation", draftId)).thenReturn(false);
        CreateCourseDraftRequest request = validRequest();
        when(aiCourseEligibilityService.isPriceEligible(request.price())).thenReturn(true);

        CourseDraftResponse response = courseService.updateDraft(draftId, request);

        assertEquals("JLPT N5 Foundation", response.title());
        assertEquals("jlpt-n5-foundation", response.slug());
        assertEquals(4, response.learningGoals().size());
        assertTrue(draft.isAiSupported());
    }

    @Test
    void deleteDraft_WhenDraftExists_ShouldDeleteCourse() {
        UUID draftId = UUID.randomUUID();
        Course draft = Course.builder()
                .id(draftId)
                .teacher(approvedTeacher)
                .title("Draft")
                .slug("draft")
                .status(CourseStatus.DRAFT)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_IdAndStatusIn(
                draftId,
                approvedTeacher.getId(),
                List.of(CourseStatus.DRAFT, CourseStatus.REJECTED, CourseStatus.FORCED_DRAFT)
        ))
                .thenReturn(Optional.of(draft));

        courseService.deleteDraft(draftId);

        verify(courseRepository).delete(draft);
    }

    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 55 — submitForReview (UC-25 Publish Course) — 5 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 55 - submitForReview (UC-25)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SubmitForReview {

    @Test
    @Order(1)
    @DisplayName("UTCID01 (N) - valid DRAFT -> PENDING, admin notified")
    void submitForReview_WhenDraftIsValid_ShouldMoveCourseToPending() {
        UUID draftId = UUID.randomUUID();
        Course draft = Course.builder()
                .id(draftId)
                .teacher(approvedTeacher)
                .title("JLPT N5 Foundation")
                .slug("jlpt-n5-foundation")
                .status(CourseStatus.DRAFT)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_IdAndStatusIn(
                draftId,
                approvedTeacher.getId(),
                List.of(CourseStatus.DRAFT, CourseStatus.REJECTED, CourseStatus.FORCED_DRAFT)
        )).thenReturn(Optional.of(draft));
        when(courseValidationService.validateCourse(draftId))
                .thenReturn(new ValidationResultResponse(true, List.of()));

        courseService.submitForReview(draftId);

        assertEquals(CourseStatus.PENDING, draft.getStatus());
        assertNotNull(draft.getSubmittedAt());
        verify(notificationService).createNotificationForAdminRole(
                "COURSE_MANAGER",
                "Khóa học mới đang chờ xét duyệt",
                "Giảng viên đã gửi khóa học \"JLPT N5 Foundation\" để xét duyệt.",
                "COURSE_REVIEW",
                "/admin/courses/approvals/" + draftId
        );
    }

    @Test
    @Order(2)
    @DisplayName("UTCID02 (N) - REJECTED course resubmitted -> PENDING, previous status audited")
    void submitForReview_WhenCourseWasRejected_ShouldResubmitAndAuditPreviousStatus() {
        UUID draftId = UUID.randomUUID();
        Course rejected = Course.builder()
                .id(draftId)
                .teacher(approvedTeacher)
                .title("JLPT N5 Foundation")
                .slug("jlpt-n5-foundation")
                .status(CourseStatus.REJECTED)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_IdAndStatusIn(
                draftId,
                approvedTeacher.getId(),
                List.of(CourseStatus.DRAFT, CourseStatus.REJECTED, CourseStatus.FORCED_DRAFT)
        )).thenReturn(Optional.of(rejected));
        when(courseValidationService.validateCourse(draftId))
                .thenReturn(new ValidationResultResponse(true, List.of()));

        courseService.submitForReview(draftId);

        assertEquals(CourseStatus.PENDING, rejected.getStatus());
        verify(auditLogService).logUserAction(
                userId,
                "TEACHER",
                "SUBMIT_COURSE",
                "COURSE",
                draftId,
                Map.of("status", CourseStatus.REJECTED.name()),
                Map.of("status", CourseStatus.PENDING.name()),
                Map.of("courseTitle", rejected.getTitle())
        );
    }

    @Test
    @Order(3)
    @DisplayName("UTCID03 (A) - validation fails -> ValidationBusinessException, status unchanged")
    void submitForReview_WhenValidationFails_ShouldKeepTheDraftUnchanged() {
        UUID draftId = UUID.randomUUID();
        Course draft = Course.builder()
                .id(draftId)
                .teacher(approvedTeacher)
                .title("Incomplete course")
                .status(CourseStatus.DRAFT)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_IdAndStatusIn(
                draftId,
                approvedTeacher.getId(),
                List.of(CourseStatus.DRAFT, CourseStatus.REJECTED, CourseStatus.FORCED_DRAFT)
        )).thenReturn(Optional.of(draft));
        when(courseValidationService.validateCourse(draftId))
                .thenReturn(new ValidationResultResponse(false, List.of()));

        assertThrows(
                com.manabihub.common.exception.ValidationBusinessException.class,
                () -> courseService.submitForReview(draftId)
        );

        assertEquals(CourseStatus.DRAFT, draft.getStatus());
        assertNull(draft.getSubmittedAt());
        verify(notificationService, never()).createNotificationForAdminRole(
                any(), any(), any(), any(), any());
        verify(auditLogService, never()).logUserAction(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @Order(4)
    @DisplayName("UTCID04 (A) - draft not found for this teacher -> COMMON_NOT_FOUND")
    void submitForReview_WhenDraftIsNotFound_ShouldThrowNotFound() {
        UUID draftId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_IdAndStatusIn(
                draftId,
                approvedTeacher.getId(),
                List.of(CourseStatus.DRAFT, CourseStatus.REJECTED, CourseStatus.FORCED_DRAFT)
        )).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> courseService.submitForReview(draftId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
        verify(courseValidationService, never()).validateCourse(any());
    }

    @Test
    @Order(5)
    @DisplayName("UTCID05 (A) - teacher workspace locked by KYC -> MSG-KYC-010 FORBIDDEN")
    void submitForReview_WhenTeacherWorkspaceIsLocked_ShouldThrowForbidden() {
        UUID draftId = UUID.randomUUID();
        TeacherProfile rejectedTeacher = new TeacherProfile();
        rejectedTeacher.setId(UUID.randomUUID());
        rejectedTeacher.setKycStatus(TeacherKycStatus.REJECTED);

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(rejectedTeacher));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> courseService.submitForReview(draftId)
        );

        assertEquals(MessageCodes.MSG_KYC_010, exception.getMessageCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        verify(courseRepository, never()).findByIdAndTeacher_IdAndStatusIn(any(), any(), any());
    }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 56 — publishCourse (UC-25 Publish Course) — 5 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 56 - publishCourse (UC-25)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PublishCourse {

    @Test
    @Order(1)
    @DisplayName("UTCID01 (N) - APPROVED and still valid -> PUBLISHED + audit")
    void publishCourse_WhenApprovedAndValid_ShouldPublishAndWriteAudit() {
        UUID courseId = UUID.randomUUID();
        Course approvedCourse = Course.builder()
                .id(courseId)
                .teacher(approvedTeacher)
                .title("JLPT N5 Foundation")
                .slug("jlpt-n5-foundation")
                .status(CourseStatus.APPROVED)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_Id(courseId, approvedTeacher.getId()))
                .thenReturn(Optional.of(approvedCourse));
        when(courseValidationService.validateCourse(courseId))
                .thenReturn(new ValidationResultResponse(true, List.of()));

        courseService.publishCourse(courseId);

        assertEquals(CourseStatus.PUBLISHED, approvedCourse.getStatus());
        assertNotNull(approvedCourse.getPublishedAt());
        verify(courseRepository).saveAndFlush(approvedCourse);
        verify(auditLogService).logUserAction(
                userId,
                "TEACHER",
                "PUBLISH_COURSE",
                "COURSE",
                courseId,
                Map.of("status", CourseStatus.APPROVED.name()),
                Map.of(
                        "status", CourseStatus.PUBLISHED.name(),
                        "publishedAt", approvedCourse.getPublishedAt().toString()
                ),
                Map.of("courseTitle", approvedCourse.getTitle())
        );
    }

    @Test
    @Order(2)
    @DisplayName("UTCID02 (A) - course still PENDING -> MSG-COURSE-007 CONFLICT")
    void publishCourse_WhenCourseIsNotApproved_ShouldRejectTransition() {
        UUID courseId = UUID.randomUUID();
        Course pendingCourse = Course.builder()
                .id(courseId)
                .teacher(approvedTeacher)
                .title("Pending course")
                .status(CourseStatus.PENDING)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_Id(courseId, approvedTeacher.getId()))
                .thenReturn(Optional.of(pendingCourse));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> courseService.publishCourse(courseId)
        );

        assertEquals(MessageCodes.MSG_COURSE_007, exception.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verify(courseValidationService, never()).validateCourse(courseId);
        verify(courseRepository, never()).saveAndFlush(any());
        verify(auditLogService, never()).logUserAction(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @Order(3)
    @DisplayName("UTCID03 (A) - validation no longer current -> ValidationBusinessException")
    void publishCourse_WhenValidationIsNoLongerCurrent_ShouldNotPublish() {
        UUID courseId = UUID.randomUUID();
        Course approvedCourse = Course.builder()
                .id(courseId)
                .teacher(approvedTeacher)
                .title("JLPT N5 Foundation")
                .status(CourseStatus.APPROVED)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_Id(courseId, approvedTeacher.getId()))
                .thenReturn(Optional.of(approvedCourse));
        when(courseValidationService.validateCourse(courseId))
                .thenReturn(new ValidationResultResponse(false, List.of()));

        assertThrows(
                com.manabihub.common.exception.ValidationBusinessException.class,
                () -> courseService.publishCourse(courseId)
        );

        assertEquals(CourseStatus.APPROVED, approvedCourse.getStatus());
        assertNull(approvedCourse.getPublishedAt());
        verify(courseRepository, never()).saveAndFlush(any());
    }

    @Test
    @Order(4)
    @DisplayName("UTCID04 (A) - course of another teacher -> COURSE_NOT_FOUND")
    void publishCourse_WhenCourseIsNotFound_ShouldThrowNotFound() {
        UUID courseId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_Id(courseId, approvedTeacher.getId()))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> courseService.publishCourse(courseId)
        );

        assertEquals(MessageCodes.COURSE_NOT_FOUND, exception.getMessageCode());
        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
        verify(courseValidationService, never()).validateCourse(any());
    }

    @Test
    @Order(5)
    @DisplayName("UTCID05 (A) - JLPT authenticity review pending -> MSG-KYC-010 FORBIDDEN")
    void publishCourse_WhenJlptAuthenticityReviewIsPending_ShouldRemainLocked() {
        UUID courseId = UUID.randomUUID();
        TeacherProfile pendingTeacher = new TeacherProfile();
        pendingTeacher.setId(UUID.randomUUID());
        pendingTeacher.setKycStatus(TeacherKycStatus.PENDING);
        pendingTeacher.setCanPublishCourse(false);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(pendingTeacher));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> courseService.publishCourse(courseId)
        );

        assertEquals(MessageCodes.MSG_KYC_010, exception.getMessageCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        verify(courseRepository, never()).findByIdAndTeacher_Id(any(), any());
    }

    @Test
    @Order(6)
    @DisplayName("UTCID06 (N) - published course -> DRAFT for safe editing + audit")
    void unpublishCourse_WhenPublished_ShouldReturnDraftForEditing() {
        UUID courseId = UUID.randomUUID();
        java.time.Instant publishedAt = java.time.Instant.parse("2026-08-10T10:15:30Z");
        Course publishedCourse = Course.builder()
                .id(courseId)
                .teacher(approvedTeacher)
                .title("JLPT N5 Foundation")
                .slug("jlpt-n5-foundation")
                .status(CourseStatus.PUBLISHED)
                .publishedAt(publishedAt)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_Id(courseId, approvedTeacher.getId()))
                .thenReturn(Optional.of(publishedCourse));

        CourseDraftResponse response = courseService.unpublishCourse(courseId);

        assertEquals(CourseStatus.DRAFT, publishedCourse.getStatus());
        assertEquals(publishedAt, publishedCourse.getPublishedAt());
        assertEquals(CourseStatus.DRAFT, response.status());
        verify(courseEditDraftService).beginEditingPublishedCourse(publishedCourse);
        verify(courseRepository).saveAndFlush(publishedCourse);
        verify(auditLogService).logUserAction(
                userId,
                "TEACHER",
                "UNPUBLISH_COURSE",
                "COURSE",
                courseId,
                Map.of(
                        "status", CourseStatus.PUBLISHED.name(),
                        "publishedAt", publishedAt.toString()
                ),
                Map.of("status", CourseStatus.DRAFT.name()),
                Map.of("courseTitle", publishedCourse.getTitle(), "reason", "Teacher requested editing")
        );
    }

    @Test
    @Order(7)
    @DisplayName("UTCID07 (A) - non-published course cannot be hidden")
    void unpublishCourse_WhenCourseIsNotPublished_ShouldRejectTransition() {
        UUID courseId = UUID.randomUUID();
        Course draft = Course.builder()
                .id(courseId)
                .teacher(approvedTeacher)
                .title("Draft course")
                .status(CourseStatus.DRAFT)
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseRepository.findByIdAndTeacher_Id(courseId, approvedTeacher.getId()))
                .thenReturn(Optional.of(draft));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> courseService.unpublishCourse(courseId)
        );

        assertEquals(MessageCodes.MSG_COURSE_007, exception.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verify(courseRepository, never()).saveAndFlush(any());
        verify(auditLogService, never()).logUserAction(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }
    }

    @Nested
    @DisplayName("(dot truoc) - createDraft validation")
    class DraftValidation {

    @Test
    void createDraft_WhenGoalsAreMissing_ShouldThrowGoalValidationError() {
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseCategoryRepository.existsByCodeAndActiveTrue("GRAMMAR")).thenReturn(true);

        CreateCourseDraftRequest request = new CreateCourseDraftRequest(
                "JLPT N5 Foundation",
                "Introduction",
                JlptLevel.N5,
                "GRAMMAR",
                null,
                "Outcomes",
                BigDecimal.valueOf(100000),
                "No prerequisites",
                "New learners",
                List.of("Goal 1", "Goal 2", "Goal 3")
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> courseService.createDraft(request));

        assertEquals(MessageCodes.MSG_GOAL_001, exception.getMessageCode());
        verify(courseRepository, never()).save(any());
    }

    @Test
    void createDraft_WhenGoalIsTooLong_ShouldThrowGoalLengthError() {
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(approvedTeacher));
        when(courseCategoryRepository.existsByCodeAndActiveTrue("GRAMMAR")).thenReturn(true);

        String longGoal = "a".repeat(161);
        CreateCourseDraftRequest request = new CreateCourseDraftRequest(
                "JLPT N5 Foundation",
                "Introduction",
                JlptLevel.N5,
                "GRAMMAR",
                null,
                "Outcomes",
                BigDecimal.valueOf(100000),
                "No prerequisites",
                "New learners",
                List.of("Goal 1", "Goal 2", "Goal 3", longGoal)
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> courseService.createDraft(request));

        assertEquals(MessageCodes.MSG_GOAL_002, exception.getMessageCode());
        verify(courseRepository, never()).save(any());
    }

    @Test
    void createDraft_WhenJlptAuthenticityReviewIsPending_ShouldAllowTeacherWorkspace() {
        TeacherProfile pendingTeacher = new TeacherProfile();
        pendingTeacher.setId(UUID.randomUUID());
        pendingTeacher.setKycStatus(TeacherKycStatus.PENDING);
        pendingTeacher.setCanPublishCourse(false);

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(teacherProfileRepository.findByUserId(userId)).thenReturn(Optional.of(pendingTeacher));
        when(courseCategoryRepository.existsByCodeAndActiveTrue("GRAMMAR")).thenReturn(true);
        when(courseRepository.existsBySlug("jlpt-n5-foundation")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(UUID.randomUUID());
            return course;
        });

        CourseDraftResponse response = courseService.createDraft(validRequest());

        assertEquals(CourseStatus.DRAFT, response.status());
        assertEquals(pendingTeacher.getId(), response.teacherId());
    }

    }

    private CreateCourseDraftRequest validRequest() {
        return new CreateCourseDraftRequest(
                "JLPT N5 Foundation",
                "Introductory Japanese course for new learners.",
                JlptLevel.N5,
                "GRAMMAR",
                "https://cdn.example.com/n5.png",
                "Learners can understand basic N5 grammar and vocabulary.",
                BigDecimal.valueOf(100000),
                "No prerequisites",
                "Students starting Japanese from zero",
                List.of(
                        "Read Hiragana and Katakana with confidence",
                        "Understand core N5 sentence patterns",
                        "Use basic greetings and classroom phrases",
                        "Prepare for beginner JLPT N5 practice"
                )
        );
    }
}
