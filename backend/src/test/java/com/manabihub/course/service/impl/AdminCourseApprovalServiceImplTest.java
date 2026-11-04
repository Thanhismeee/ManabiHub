package com.manabihub.course.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.course.dto.request.CourseReviewRequest;
import com.manabihub.course.dto.response.CourseApprovalDetailResponse;
import com.manabihub.course.dto.response.ValidationError;
import com.manabihub.course.dto.response.ValidationResultResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseApprovalDecision;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.CourseApprovalDecisionEnum;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseApprovalDecisionRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.course.service.CourseValidationService;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AdminCourseApprovalServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseApprovalDecisionRepository decisionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CourseEditDraftService courseEditDraftService;

    @Mock
    private CourseValidationService courseValidationService;

    @InjectMocks
    private AdminCourseApprovalServiceImpl courseApprovalService;

    private UUID adminId;
    private UUID courseId;
    private UUID teacherUserId;
    private Course course;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        teacherUserId = UUID.randomUUID();

        AppUser teacherUser = new AppUser();
        teacherUser.setId(teacherUserId);
        teacherUser.setEmail("teacher@manabihub.local");
        teacherUser.setFullName("Teacher");

        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(UUID.randomUUID());
        teacher.setUser(teacherUser);
        teacher.setKycStatus(TeacherKycStatus.APPROVED);
        teacher.setCanPublishCourse(true);

        course = Course.builder()
                .id(courseId)
                .teacher(teacher)
                .title("N5 for beginners")
                .status(CourseStatus.PENDING)
                .build();

        org.mockito.Mockito.lenient()
                .when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        org.mockito.Mockito.lenient()
                .when(courseRepository.findByIdForApprovalReview(courseId)).thenReturn(Optional.of(course));
        org.mockito.Mockito.lenient()
                .when(courseEditDraftService.resolveEditableCourse(any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(courseValidationService.validateCourseForReview(courseId))
                .thenReturn(new ValidationResultResponse(true, List.of()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 64 — reviewCourse (UC-29 Approve Course Publication) — 10 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 64 - reviewCourse (UC-29)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ReviewCourse {

    @Test
    @Order(1)
    @DisplayName("UTCID01 (N) - System Admin cannot make an operational course decision")
    void reviewCourse_RejectsSystemAdminOperationalDecision() {
        when(courseRepository.hasAdminRole(adminId, List.of("COURSE_MANAGER"))).thenReturn(false);
        CourseReviewRequest request = CourseReviewRequest.builder()
                .action("APPROVE")
                .build();

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> courseApprovalService.reviewCourse(adminId, courseId, request)
        );

        assertEquals(MessageCodes.ADMIN_PERMISSION_DENIED, error.getMessageCode());
        verify(auditLogRepository, never()).save(any());
    }

    @ParameterizedTest
    @Order(2)
    @DisplayName("UTCID02-04 (N) - APPROVE / REJECT / REQUEST_CORRECTION -> status, decision, notify, audit")
    @CsvSource({
            "APPROVE,,APPROVED,APPROVED,COURSE_APPROVED",
            "REJECT,Missing evidence,REJECTED,REJECTED,COURSE_REJECTED",
            "REQUEST_CORRECTION,Fix lesson content,DRAFT,CORRECTION_REQUIRED,COURSE_CORRECTION_REQUESTED"
    })
    void reviewCourse_ShouldWriteDatabaseCompatibleInternalAdminAudit(
            String action,
            String reason,
            CourseStatus expectedStatus,
            CourseApprovalDecisionEnum expectedDecision,
            String expectedAuditAction
    ) {
        mockCourseManagerAccess();
        CourseReviewRequest request = CourseReviewRequest.builder()
                .action(action)
                .reason(reason)
                .build();

        courseApprovalService.reviewCourse(adminId, courseId, request);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();

        assertEquals("INTERNAL_ADMIN", auditLog.getActorType());
        assertEquals(adminId, auditLog.getActorAdminId());
        assertEquals("COURSE_MANAGER", auditLog.getActorRoleCode());
        assertEquals(expectedAuditAction, auditLog.getAction());
        assertEquals("COURSE", auditLog.getTargetType());
        assertEquals(courseId, auditLog.getTargetId());
        assertEquals("PENDING", auditLog.getBeforeValue().get("status"));
        assertEquals(expectedStatus.name(), auditLog.getAfterValue().get("status"));

        ArgumentCaptor<CourseApprovalDecision> decisionCaptor =
                ArgumentCaptor.forClass(CourseApprovalDecision.class);
        verify(decisionRepository).save(decisionCaptor.capture());
        assertEquals(expectedDecision, decisionCaptor.getValue().getDecision());

        verify(notificationService).createNotification(
                eq(teacherUserId),
                eq("teacher@manabihub.local"),
                anyString(),
                anyString(),
                eq("COURSE_APPROVAL"),
                eq("/teacher/courses")
        );
        assertEquals(expectedStatus, course.getStatus());
    }

    @Test
    @Order(5)
    @DisplayName("UTCID05 (A) - course not found -> MSG-COM-001")
    void reviewCourse_CourseNotFound_IsRejected() {
        mockCourseManagerAccess();
        when(courseRepository.findByIdForApprovalReview(courseId)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> courseApprovalService.reviewCourse(adminId, courseId, approve())
        );

        assertEquals("MSG-COM-001", error.getMessageCode());
        verify(courseRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @Order(6)
    @DisplayName("UTCID06 (A) - course is not PENDING -> 409 conflict")
    void reviewCourse_CourseNotPending_IsRejected() {
        mockCourseManagerAccess();
        course.setStatus(CourseStatus.APPROVED);
        when(courseRepository.findByIdForApprovalReview(courseId)).thenReturn(Optional.of(course));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> courseApprovalService.reviewCourse(adminId, courseId, approve())
        );

        assertEquals("MSG-COURSE-REVIEW-STATE", error.getMessageCode());
        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        assertEquals(
                "Yêu cầu xét duyệt này đã được xử lý. Chỉ khóa học đang chờ phê duyệt mới có thể nhận quyết định.",
                error.getMessage()
        );
        assertEquals(CourseStatus.APPROVED, course.getStatus());
        verify(decisionRepository, never()).save(any());
    }

    @Test
    @Order(7)
    @DisplayName("UTCID07 (A) - REJECT without a reason -> MSG-COM-002")
    void reviewCourse_RejectWithoutReason_IsRejected() {
        mockCourseManagerAccess();
        CourseReviewRequest request = CourseReviewRequest.builder().action("REJECT").build();

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> courseApprovalService.reviewCourse(adminId, courseId, request)
        );

        assertEquals("MSG-COM-002", error.getMessageCode());
        assertEquals(CourseStatus.PENDING, course.getStatus());
        verify(courseRepository, never()).save(any());
    }

    @Test
    @Order(8)
    @DisplayName("UTCID08 (A) - REQUEST_CORRECTION with a blank reason -> MSG-COM-002")
    void reviewCourse_CorrectionWithBlankReason_IsRejected() {
        mockCourseManagerAccess();
        CourseReviewRequest request = CourseReviewRequest.builder()
                .action("REQUEST_CORRECTION")
                .reason("   ")
                .build();

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> courseApprovalService.reviewCourse(adminId, courseId, request)
        );

        assertEquals("MSG-COM-002", error.getMessageCode());
        assertEquals(CourseStatus.PENDING, course.getStatus());
        verify(courseRepository, never()).save(any());
    }

    @Test
    @Order(9)
    @DisplayName("UTCID09 (A) - unknown action -> MSG-COM-004")
    void reviewCourse_UnknownAction_IsRejected() {
        mockCourseManagerAccess();
        CourseReviewRequest request = CourseReviewRequest.builder().action("ARCHIVE").build();

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> courseApprovalService.reviewCourse(adminId, courseId, request)
        );

        assertEquals("MSG-COM-004", error.getMessageCode());
        verify(courseRepository, never()).save(any());
        verify(notificationService, never()).createNotification(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @Order(10)
    @DisplayName("UTCID10 (A) - actor is not Course Manager -> ADMIN_PERMISSION_DENIED")
    void reviewCourse_WithoutReviewerRole_IsRejected() {
        when(courseRepository.hasAdminRole(adminId, List.of("COURSE_MANAGER"))).thenReturn(false);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> courseApprovalService.reviewCourse(adminId, courseId, approve())
        );

        assertEquals(MessageCodes.ADMIN_PERMISSION_DENIED, error.getMessageCode());
        verify(courseRepository, never()).findById(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @Order(11)
    @DisplayName("UTCID11 (A) - APPROVE when publication validation fails -> blocked")
    void reviewCourse_ApproveInvalidCourse_IsRejectedWithoutStateChange() {
        mockCourseManagerAccess();
        when(courseValidationService.validateCourseForReview(courseId)).thenReturn(
                new ValidationResultResponse(false, List.of(
                        new ValidationError("MSG-FINAL-001", "Chưa cấu hình bài kiểm tra cuối khóa.", "error")
                ))
        );

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> courseApprovalService.reviewCourse(adminId, courseId, approve())
        );

        assertEquals("MSG-COURSE-004", error.getMessageCode());
        assertEquals(CourseStatus.PENDING, course.getStatus());
        verify(courseEditDraftService, never()).applyApprovedDraft(any());
        verify(courseRepository, never()).save(any());
        verify(decisionRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    private CourseReviewRequest approve() {
        return CourseReviewRequest.builder().action("APPROVE").build();
    }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Not part of Report 5.1 — kept from the earlier iteration
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("(khong thuoc sheet nao) - getDetail")
    class GetDetail {

        @Test
        void getDetail_DoesNotFabricateCopyrightAgreementEvidence() {
            mockCourseManagerAccess();

            CourseApprovalDetailResponse detail = courseApprovalService.getDetail(adminId, courseId);

            assertNull(detail.getPolicyEvidence());
        }

        @Test
        void getDetail_ReturnsValidationReasonsAndBlocksApproval() {
            mockCourseManagerAccess();
            when(courseValidationService.validateCourseForReview(courseId)).thenReturn(
                    new ValidationResultResponse(false, List.of(
                            new ValidationError("MSG-COURSE-015", "Khóa học cần tối thiểu 5 bài học.", "error")
                    ))
            );

            CourseApprovalDetailResponse detail = courseApprovalService.getDetail(adminId, courseId);

            assertFalse(detail.isApprovalReady());
            assertEquals(1, detail.getValidationErrors().size());
            assertTrue(detail.getReviewCriteria().stream()
                    .anyMatch(criterion -> "CURRICULUM".equals(criterion.code()) && !criterion.passed()));
        }

        @Test
        void getDetail_MapsCompleteSubmittedRevisionInsteadOfEmptyFallbacks() {
            mockCourseManagerAccess();
            Course reviewCourse = Course.builder()
                    .id(courseId)
                    .teacher(course.getTeacher())
                    .title("Complete N5 course")
                    .introduction("Introduction")
                    .description("Description")
                    .jlptLevel(JlptLevel.N5)
                    .category("VOCABULARY")
                    .thumbnailUrl("/api/v1/teacher/courses/thumbnails/course.png")
                    .outcomes("Outcomes")
                    .price(BigDecimal.ZERO)
                    .currency("VND")
                    .status(CourseStatus.PENDING)
                    .build();
            CourseModule module = CourseModule.builder()
                    .id(UUID.randomUUID())
                    .title("Module 1")
                    .orderIndex(1)
                    .build();
            module.addBlock(LessonBlock.builder()
                    .id(UUID.randomUUID())
                    .type(LessonBlockType.VIDEO)
                    .title("Video lesson")
                    .durationMinutes(31)
                    .orderIndex(1)
                    .build());
            reviewCourse.addModule(module);
            reviewCourse.setFinalTest(FinalTest.builder()
                    .id(UUID.randomUUID())
                    .course(reviewCourse)
                    .timeLimitMinutes(60)
                    .passingScore(90)
                    .maxRetakes(3)
                    .jlptLevel(JlptLevel.N5)
                    .skillFocus("Tong hop")
                    .build());
            when(courseEditDraftService.resolveEditableCourse(course)).thenReturn(reviewCourse);

            CourseApprovalDetailResponse detail = courseApprovalService.getDetail(adminId, courseId);

            assertEquals(JlptLevel.N5, detail.getJlptLevel());
            assertEquals("VOCABULARY", detail.getCategory());
            assertEquals("/api/v1/teacher/courses/thumbnails/course.png", detail.getThumbnailUrl());
            assertEquals(BigDecimal.ZERO, detail.getPrice());
            assertEquals(1, detail.getModuleCount());
            assertEquals(1, detail.getLessonBlocksCount());
            assertEquals(31, detail.getTotalVideoDurationMinutes());
            assertTrue(detail.isFinalTestIncluded());
            assertNotNull(detail.getFinalTest());
            assertTrue(detail.isApprovalReady());
        }
    }

    private void mockCourseManagerAccess() {
        when(courseRepository.hasAdminRole(adminId, List.of("COURSE_MANAGER"))).thenReturn(true);
    }
}
