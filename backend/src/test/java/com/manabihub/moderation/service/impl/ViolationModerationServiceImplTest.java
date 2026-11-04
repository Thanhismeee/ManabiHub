package com.manabihub.moderation.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.IdentityTeacherProfileRepository;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.moderation.dto.request.ResolveViolationRequest;
import com.manabihub.moderation.entity.ModerationDecision;
import com.manabihub.moderation.entity.ViolationReport;
import com.manabihub.moderation.enums.ModerationActionType;
import com.manabihub.moderation.enums.ModerationDecisionType;
import com.manabihub.moderation.enums.EvidenceRequestedFrom;
import com.manabihub.moderation.enums.ViolationReportStatus;
import com.manabihub.moderation.event.ModerationNotificationEvent;
import com.manabihub.moderation.repository.ModerationActionRecordRepository;
import com.manabihub.moderation.repository.ModerationDecisionRepository;
import com.manabihub.moderation.repository.ViolationEvidenceRepository;
import com.manabihub.moderation.repository.ViolationReportRepository;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.order.repository.OrderItemRepository;
import com.manabihub.review.entity.CourseReview;
import com.manabihub.review.enums.CourseReviewStatus;
import com.manabihub.review.repository.CourseReviewRepository;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.repository.WalletRepository;
import com.manabihub.violation.service.ViolationEvidenceStorageService;
import com.manabihub.wallet.enums.WalletOwnerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViolationModerationServiceImpl#resolveViolation} — UC-30 Resolve
 * Violation Report.
 * <p>
 * Every test in this class targets the same function, so Surefire already reports it as a single
 * summary line — it maps 1:1 to Report 5.1 sheet 53 {@code resolveViolation}. No {@code @Nested}
 * grouping is needed here.
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ViolationModerationServiceImplTest {

    @Mock private ViolationReportRepository reportRepository;
    @Mock private ViolationEvidenceRepository evidenceRepository;
    @Mock private ModerationDecisionRepository decisionRepository;
    @Mock private ModerationActionRecordRepository actionRecordRepository;
    @Mock private InternalAdminAccountRepository adminRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private CourseReviewRepository courseReviewRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private IdentityTeacherProfileRepository teacherProfileRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ViolationEvidenceStorageService evidenceStorageService;

    @InjectMocks
    private ViolationModerationServiceImpl moderationService;

    private UUID reportId;
    private UUID adminId;
    private UUID teacherProfileId;
    private UUID teacherUserId;
    private UUID courseId;
    private ViolationReport report;
    private InternalAdminAccount admin;
    private AppUser reporterUser;
    private AppUser teacherUser;
    private Course course;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        reportId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        teacherProfileId = UUID.randomUUID();
        teacherUserId = UUID.randomUUID();
        courseId = UUID.randomUUID();

        teacherUser = AppUser.builder()
                .id(teacherUserId)
                .email("teacher@test.com")
                .fullName("Teacher")
                .userStatus(AccountStatus.ACTIVE)
                .build();
        reporterUser = AppUser.builder()
                .id(UUID.randomUUID())
                .email("reporter@test.com")
                .fullName("Reporter")
                .userStatus(AccountStatus.ACTIVE)
                .build();

        com.manabihub.kyc.domain.AppUser kycUser =
                new com.manabihub.kyc.domain.AppUser();
        kycUser.setId(teacherUserId);
        kycUser.setEmail("teacher@test.com");
        kycUser.setFullName("Teacher");

        TeacherProfile profile = new TeacherProfile();
        profile.setId(teacherProfileId);
        profile.setUser(kycUser);

        course = Course.builder()
                .id(courseId)
                .teacher(profile)
                .title("Japanese N5")
                .status(CourseStatus.PUBLISHED)
                .price(BigDecimal.valueOf(100_000))
                .build();

        report = ViolationReport.builder()
                .id(reportId)
                .reporter(reporterUser)
                .targetType("COURSE")
                .targetId(courseId)
                .reason("Copyright report")
                .status(ViolationReportStatus.PENDING_REVIEW)
                .build();

        Role role = new Role();
        role.setCode(RoleCode.COURSE_MANAGER);
        admin = new InternalAdminAccount();
        admin.setId(adminId);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setRole(role);

        wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setTeacher(profile);
        wallet.setBalance(BigDecimal.valueOf(5_000_000));
        wallet.setFrozenBalance(BigDecimal.ZERO);
        wallet.setCurrency("VND");
    }

    @Test
    @Order(1)
    @DisplayName("UTCID01 (N) - DISMISSED -> RESOLVED_NO_VIOLATION, no enforcement")
    void dismissResolvesWithoutEnforcementAndPublishesReporterNotification() {
        stubResolvePermission();
        stubSuccessfulResolutionReads();
        ResolveViolationRequest request = request(ModerationDecisionType.DISMISSED, List.of());

        moderationService.resolveViolation(reportId, request, adminId);

        assertEquals(ViolationReportStatus.RESOLVED_NO_VIOLATION, report.getStatus());
        verify(courseRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(eventPublisher, times(2))
                .publishEvent(any(ModerationNotificationEvent.class));
    }

    @Test
    @Order(2)
    @DisplayName("UTCID02 (N) - UPHELD + FORCE_DRAFT -> course leaves the public catalog")
    void upheldForceDraftRemovesCourseFromPublicCatalog() {
        stubResolvePermission();
        when(adminRepository.hasPermission(adminId, "VIOLATION_CONTENT_ENFORCE"))
                .thenReturn(true);
        stubSuccessfulResolutionReads();
        when(courseRepository.findByIdForModeration(courseId)).thenReturn(Optional.of(course));

        moderationService.resolveViolation(
                reportId,
                request(ModerationDecisionType.UPHELD, List.of(ModerationActionType.FORCE_DRAFT)),
                adminId
        );

        assertEquals(CourseStatus.FORCED_DRAFT, course.getStatus());
        verify(courseRepository).save(course);
        verify(eventPublisher, times(2))
                .publishEvent(any(ModerationNotificationEvent.class));
    }

    @Test
    @Order(3)
    @DisplayName("UTCID03 (N) - UPHELD + BAN + FREEZE -> user LOCKED, wallet frozen")
    void severeActionsUseUserIdForBanAndTeacherProfileIdForWallet() {
        Role systemRole = new Role();
        systemRole.setCode(RoleCode.SYSTEM_ADMIN);
        admin.setRole(systemRole);
        stubResolvePermission();
        when(adminRepository.hasPermission(adminId, "VIOLATION_SEVERE_ENFORCE"))
                .thenReturn(true);
        stubSuccessfulResolutionReads();
        when(courseRepository.findByIdForModeration(courseId)).thenReturn(Optional.of(course));
        when(appUserRepository.findByIdForUpdate(teacherUserId))
                .thenReturn(Optional.of(teacherUser));
        when(walletRepository.findByOwnerTypeAndTeacher_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.TEACHER, teacherProfileId))
                .thenReturn(Optional.of(wallet));

        moderationService.resolveViolation(
                reportId,
                request(
                        ModerationDecisionType.UPHELD,
                        List.of(
                                ModerationActionType.BAN_ACCOUNT,
                                ModerationActionType.FREEZE_BALANCE
                        )
                ),
                adminId
        );

        assertEquals(AccountStatus.LOCKED, teacherUser.getUserStatus());
        assertTrue(wallet.isFrozen());
        verify(appUserRepository).findByIdForUpdate(teacherUserId);
        verify(walletRepository).findByOwnerTypeAndTeacher_IdForUpdate(com.manabihub.wallet.enums.WalletOwnerType.TEACHER, teacherProfileId);
    }

    @Test
    @Order(8)
    @DisplayName("UTCID08 (A) - report already resolved -> CONFLICT")
    void secondResolutionReturnsConflict() {
        stubResolvePermission();
        report.setStatus(ViolationReportStatus.RESOLVED_UPHELD);
        when(reportRepository.findByIdLocked(reportId)).thenReturn(Optional.of(report));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> moderationService.resolveViolation(
                        reportId,
                        request(ModerationDecisionType.DISMISSED, List.of()),
                        adminId
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verify(decisionRepository, never()).save(any());
    }

    @Test
    @Order(9)
    @DisplayName("UTCID09 (A) - DISMISSED + BAN_ACCOUNT -> invalid action combination")
    void dismissedDecisionRejectsClientSuppliedBanAction() {
        stubResolvePermission();
        ResolveViolationRequest request = request(
                ModerationDecisionType.DISMISSED,
                List.of(ModerationActionType.BAN_ACCOUNT)
        );

        assertThrows(
                BusinessException.class,
                () -> moderationService.resolveViolation(reportId, request, adminId)
        );
        verify(reportRepository, never()).findByIdLocked(any());
    }

    @Test
    @Order(10)
    @DisplayName("UTCID10 (A) - UPHELD + NONE -> invalid action combination")
    void upheldDecisionRejectsNoneAction() {
        stubResolvePermission();
        ResolveViolationRequest request = request(
                ModerationDecisionType.UPHELD,
                List.of(ModerationActionType.NONE)
        );

        assertThrows(
                BusinessException.class,
                () -> moderationService.resolveViolation(reportId, request, adminId)
        );
        verify(reportRepository, never()).findByIdLocked(any());
    }

    @Test
    @Order(11)
    @DisplayName("UTCID11 (A) - no VIOLATION_SEVERE_ENFORCE -> FORBIDDEN")
    void courseManagerCannotApplySevereActionWithoutPermission() {
        stubResolvePermission();
        stubDecisionSave();
        when(reportRepository.findByIdLocked(reportId)).thenReturn(Optional.of(report));
        when(courseRepository.findByIdForModeration(courseId)).thenReturn(Optional.of(course));
        when(appUserRepository.findById(teacherUserId)).thenReturn(Optional.of(teacherUser));
        when(adminRepository.hasPermission(adminId, "VIOLATION_SEVERE_ENFORCE"))
                .thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> moderationService.resolveViolation(
                        reportId,
                        request(
                                ModerationDecisionType.UPHELD,
                                List.of(ModerationActionType.BAN_ACCOUNT)
                        ),
                        adminId
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        verify(appUserRepository, never()).save(any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @Order(12)
    @DisplayName("UTCID12 (A) - blank decision note -> rejected before locking the report")
    void missingDecisionNoteDoesNotLockOrMutateReport() {
        stubResolvePermission();
        ResolveViolationRequest request = request(ModerationDecisionType.DISMISSED, List.of());
        request.setDecisionNote("   ");

        assertThrows(
                BusinessException.class,
                () -> moderationService.resolveViolation(reportId, request, adminId)
        );
        verify(reportRepository, never()).findByIdLocked(any());
    }

    @Test
    @Order(6)
    @DisplayName("UTCID06 (N) - PENDING_EVIDENCE -> no enforcement, reporter notified")
    void pendingEvidenceDoesNotApplySevereActionAndNotifiesReporter() {
        stubResolvePermission();
        stubSuccessfulResolutionReads();
        when(courseRepository.findByIdForModeration(courseId)).thenReturn(Optional.of(course));
        ResolveViolationRequest request =
                request(ModerationDecisionType.PENDING_EVIDENCE, List.of());
        request.setEvidenceRequestedFrom(EvidenceRequestedFrom.REPORTER);

        moderationService.resolveViolation(
                reportId,
                request,
                adminId
        );

        assertEquals(ViolationReportStatus.PENDING_EVIDENCE, report.getStatus());
        verify(appUserRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(eventPublisher).publishEvent(any(ModerationNotificationEvent.class));
    }

    @Test
    @Order(13)
    @DisplayName("UTCID13 (A) - USER report cannot remove arbitrary course content")
    void userReportCannotBeUsedToRemoveArbitraryCourseContent() {
        report.setTargetType("USER");
        report.setTargetId(teacherUserId);
        stubResolvePermission();
        when(adminRepository.hasPermission(adminId, "VIOLATION_CONTENT_ENFORCE"))
                .thenReturn(true);
        when(reportRepository.findByIdLocked(reportId)).thenReturn(Optional.of(report));
        stubDecisionSave();
        when(appUserRepository.findByIdForUpdate(teacherUserId))
                .thenReturn(Optional.of(teacherUser));
        when(teacherProfileRepository.findByUser_Id(teacherUserId))
                .thenReturn(Optional.empty());
        ResolveViolationRequest request = request(
                ModerationDecisionType.UPHELD,
                List.of(ModerationActionType.REMOVE_CONTENT)
        );
        request.setTargetIds(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> moderationService.resolveViolation(reportId, request, adminId)
        );

        assertEquals(MessageCodes.MODERATION_INVALID_ACTION, exception.getMessageCode());
        verify(lessonBlockRepository, never()).findByIdForModeration(any());
        verify(courseReviewRepository, never()).findByIdForModeration(any());
    }

    @Test
    @Order(4)
    @DisplayName("UTCID04 (N) - REVIEW report + REMOVE_CONTENT -> only the review is hidden")
    void removingReportedReviewHidesOnlyReviewAndNotifiesReporterAndAuthor() {
        CourseReview review = reviewBy(newReviewAuthor());
        report.setTargetType("REVIEW");
        report.setTargetId(review.getId());
        stubResolvePermission();
        when(adminRepository.hasPermission(adminId, "VIOLATION_CONTENT_ENFORCE"))
                .thenReturn(true);
        stubSuccessfulReviewResolutionReads(review);

        moderationService.resolveViolation(
                reportId,
                request(
                        ModerationDecisionType.UPHELD,
                        List.of(ModerationActionType.REMOVE_CONTENT)
                ),
                adminId
        );

        assertEquals(CourseReviewStatus.HIDDEN, review.getStatus());
        assertEquals(CourseStatus.PUBLISHED, course.getStatus());
        verify(courseRepository, never()).save(any());
        verify(courseReviewRepository).save(review);

        ArgumentCaptor<ModerationNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ModerationNotificationEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        Set<UUID> recipientIds = eventCaptor.getAllValues().stream()
                .map(ModerationNotificationEvent::recipientUserId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(
                Set.of(reporterUser.getId(), review.getEnrollment().getStudent().getUser().getId()),
                recipientIds
        );
        assertFalse(recipientIds.contains(teacherUserId));
    }

    @Test
    @Order(5)
    @DisplayName("UTCID05 (N) - REVIEW report + BAN -> the review author is locked, not the teacher")
    void banningReportedReviewLocksAuthorInsteadOfCourseTeacher() {
        AppUser reviewAuthor = newReviewAuthor();
        CourseReview review = reviewBy(reviewAuthor);
        report.setTargetType("REVIEW");
        report.setTargetId(review.getId());
        stubResolvePermission();
        when(adminRepository.hasPermission(adminId, "VIOLATION_SEVERE_ENFORCE"))
                .thenReturn(true);
        stubSuccessfulReviewResolutionReads(review);
        when(appUserRepository.findByIdForUpdate(reviewAuthor.getId()))
                .thenReturn(Optional.of(reviewAuthor));

        moderationService.resolveViolation(
                reportId,
                request(
                        ModerationDecisionType.UPHELD,
                        List.of(ModerationActionType.BAN_ACCOUNT)
                ),
                adminId
        );

        assertEquals(AccountStatus.LOCKED, reviewAuthor.getUserStatus());
        assertEquals(AccountStatus.ACTIVE, teacherUser.getUserStatus());
        verify(appUserRepository).findByIdForUpdate(reviewAuthor.getId());
        verify(appUserRepository, never()).findByIdForUpdate(teacherUserId);
        verify(walletRepository, never()).findByOwnerTypeAndTeacher_IdForUpdate(eq(com.manabihub.wallet.enums.WalletOwnerType.TEACHER), any());
    }

    @Test
    @Order(7)
    @DisplayName("UTCID07 (N) - reporter is the affected user -> a single notification")
    void outcomeNotificationIsDeduplicatedWhenReporterIsAffectedUser() {
        report.setReporter(teacherUser);
        stubResolvePermission();
        stubSuccessfulResolutionReads();

        moderationService.resolveViolation(
                reportId,
                request(ModerationDecisionType.DISMISSED, List.of()),
                adminId
        );

        verify(eventPublisher).publishEvent(any(ModerationNotificationEvent.class));
    }

    @Test
    @Order(14)
    @DisplayName("UTCID14 (A) - report not found -> NOT_FOUND, nothing is written")
    void unknownReportIsRejected() {
        stubResolvePermission();
        when(reportRepository.findByIdLocked(reportId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> moderationService.resolveViolation(
                        reportId,
                        request(ModerationDecisionType.DISMISSED, List.of()),
                        adminId
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
        verify(decisionRepository, never()).save(any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @Order(15)
    @DisplayName("UTCID15 (A) - admin without VIOLATION_RESOLVE -> FORBIDDEN")
    void adminWithoutTheResolvePermissionIsRejected() {
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminRepository.hasPermission(adminId, "VIOLATION_RESOLVE")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> moderationService.resolveViolation(
                        reportId,
                        request(ModerationDecisionType.DISMISSED, List.of()),
                        adminId
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        verify(reportRepository, never()).findByIdLocked(any());
        verify(decisionRepository, never()).save(any());
    }

    private void stubResolvePermission() {
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminRepository.hasPermission(adminId, "VIOLATION_RESOLVE")).thenReturn(true);
    }

    private void stubSuccessfulResolutionReads() {
        when(reportRepository.findByIdLocked(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(ViolationReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubDecisionSave();
        when(actionRecordRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceRepository.findByViolationReport_IdOrderByCreatedAtAsc(reportId))
                .thenReturn(List.of());
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(appUserRepository.findById(teacherUserId)).thenReturn(Optional.of(teacherUser));
        when(decisionRepository.findByViolationReportIdOrderByCreatedAtDesc(reportId))
                .thenReturn(Collections.emptyList());
        when(orderItemRepository.countPaidPurchasersByCourseId(courseId)).thenReturn(0L);
        when(reportRepository.countByTargetTypeIgnoreCaseAndTargetIdAndStatus(
                "COURSE",
                courseId,
                ViolationReportStatus.RESOLVED_UPHELD
        )).thenReturn(0L);
    }

    private void stubSuccessfulReviewResolutionReads(CourseReview review) {
        when(reportRepository.findByIdLocked(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(ViolationReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubDecisionSave();
        when(actionRecordRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceRepository.findByViolationReport_IdOrderByCreatedAtAsc(reportId))
                .thenReturn(List.of());
        when(courseReviewRepository.findByIdForModeration(review.getId()))
                .thenReturn(Optional.of(review));
        when(courseReviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(decisionRepository.findByViolationReportIdOrderByCreatedAtDesc(reportId))
                .thenReturn(Collections.emptyList());
        when(orderItemRepository.countPaidPurchasersByCourseId(courseId)).thenReturn(0L);
        when(reportRepository.countByTargetTypeIgnoreCaseAndTargetIdAndStatus(
                "REVIEW",
                review.getId(),
                ViolationReportStatus.RESOLVED_UPHELD
        )).thenReturn(0L);
    }

    private AppUser newReviewAuthor() {
        return AppUser.builder()
                .id(UUID.randomUUID())
                .email("reviewer@test.com")
                .fullName("Review Author")
                .userStatus(AccountStatus.ACTIVE)
                .build();
    }

    private CourseReview reviewBy(AppUser reviewAuthor) {
        StudentProfile student = StudentProfile.builder()
                .id(UUID.randomUUID())
                .user(reviewAuthor)
                .build();
        Enrollment enrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .student(student)
                .course(course)
                .build();
        return CourseReview.builder()
                .id(UUID.randomUUID())
                .enrollment(enrollment)
                .rating(1)
                .reviewText("Reported review")
                .status(CourseReviewStatus.APPROVED)
                .build();
    }

    private void stubDecisionSave() {
        when(decisionRepository.save(any(ModerationDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ResolveViolationRequest request(
            ModerationDecisionType decision,
            List<ModerationActionType> actions
    ) {
        ResolveViolationRequest request = new ResolveViolationRequest();
        request.setDecision(decision);
        request.setDecisionNote("Documented moderation reason");
        request.setActions(actions);
        return request;
    }
}
