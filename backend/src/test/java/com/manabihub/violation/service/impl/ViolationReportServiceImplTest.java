package com.manabihub.violation.service.impl;

import com.manabihub.course.entity.Course;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.moderation.entity.ViolationEvidence;
import com.manabihub.moderation.repository.ViolationEvidenceRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.violation.dto.ViolationReportRequest;
import com.manabihub.violation.dto.ViolationReportResponse;
import com.manabihub.violation.entity.ViolationReport;
import com.manabihub.violation.enums.ViolationStatus;
import com.manabihub.violation.enums.ViolationTargetType;
import com.manabihub.violation.mapper.ViolationReportMapper;
import com.manabihub.violation.repository.ViolationReportRepository;
import com.manabihub.violation.service.ViolationEvidenceStorageService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.LessonBlock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViolationReportServiceImpl#submitReport} — UC-20 Report Course Violation.
 * <p>
 * Every test targets the same function, so Surefire reports this class as a single summary line
 * = Report 5.1 sheet 63 {@code submitReport}. No {@code @Nested} grouping is needed.
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ViolationReportServiceImplTest {

    @Mock
    private ViolationReportRepository violationReportRepository;

    @Mock
    private ViolationReportMapper violationReportMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ViolationEvidenceRepository evidenceRepository;

    @Mock
    private ViolationEvidenceStorageService evidenceStorageService;

    @InjectMocks
    private ViolationReportServiceImpl violationReportService;

    private UUID reporterId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(violationReportService, "spamWindowMinutes", 60);
        reporterId = UUID.randomUUID();
        targetId = UUID.randomUUID();
    }

    private ViolationReportRequest request(ViolationTargetType targetType) {
        ViolationReportRequest request = new ViolationReportRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setReason("Misleading course content");
        request.setDescription("The course video does not match the public description.");
        return request;
    }

    @Test
    @Order(1)
    @DisplayName("UTCID01 (N) - COURSE target -> report saved and Course Manager notified")
    void submitReport_CreatesNotificationWithRoutableAdminActionUrl() {
        UUID reportId = UUID.randomUUID();
        AppUser reporter = AppUser.builder().id(reporterId).build();

        ViolationReportRequest request = request(ViolationTargetType.COURSE);

        when(entityManager.find(Course.class, targetId)).thenReturn(new Course());
        when(entityManager.find(AppUser.class, reporterId, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(reporter);
        when(violationReportRepository.isDuplicateReport(
                eq(reporterId),
                eq(ViolationTargetType.COURSE),
                eq(targetId),
                any(Instant.class)
        )).thenReturn(false);
        when(violationReportRepository.save(any(ViolationReport.class)))
                .thenAnswer(invocation -> {
                    ViolationReport saved = invocation.getArgument(0);
                    saved.setId(reportId);
                    return saved;
                });
        when(violationReportMapper.toResponse(any(ViolationReport.class)))
                .thenReturn(new ViolationReportResponse());

        MockMultipartFile evidenceFile = new MockMultipartFile(
                "evidence",
                "screenshot.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        com.manabihub.moderation.entity.ViolationReport moderationReport =
                com.manabihub.moderation.entity.ViolationReport.builder().id(reportId).build();
        when(entityManager.getReference(
                com.manabihub.moderation.entity.ViolationReport.class,
                reportId
        )).thenReturn(moderationReport);
        when(evidenceStorageService.store(reportId, evidenceFile)).thenReturn(
                new ViolationEvidenceStorageService.StoredEvidence(
                        reportId + "/evidence.png",
                        "screenshot.png",
                        "image/png",
                        "IMAGE"
                )
        );
        when(evidenceStorageService.toStoredReference(reportId + "/evidence.png"))
                .thenReturn("private:violation-evidence:" + reportId + "/evidence.png");

        violationReportService.submitReport(request, List.of(evidenceFile), reporterId);

        verify(notificationService).createNotificationForAdminRole(
                eq("COURSE_MANAGER"),
                eq("Có báo cáo vi phạm mới"),
                contains(targetId.toString()),
                eq("VIOLATION_REPORT"),
                eq("/admin/violations/" + reportId)
        );
        ArgumentCaptor<ViolationEvidence> savedEvidence = ArgumentCaptor.forClass(ViolationEvidence.class);
        verify(evidenceRepository).save(savedEvidence.capture());
        assertEquals("screenshot.png", savedEvidence.getValue().getDisplayName());
        assertEquals("IMAGE", savedEvidence.getValue().getEvidenceType());
        assertEquals(reporter, savedEvidence.getValue().getSubmittedBy());
    }

    @Test
    @Order(2)
    @DisplayName("UTCID02 (N) - LESSON_BLOCK target -> report saved with PENDING_REVIEW")
    void submitReport_LessonBlockTarget_IsAccepted() {
        AppUser reporter = AppUser.builder().id(reporterId).build();
        ViolationReportRequest request = request(ViolationTargetType.LESSON_BLOCK);

        when(entityManager.find(LessonBlock.class, targetId)).thenReturn(new LessonBlock());
        when(entityManager.find(AppUser.class, reporterId, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(reporter);
        when(violationReportRepository.isDuplicateReport(
                eq(reporterId),
                eq(ViolationTargetType.LESSON_BLOCK),
                eq(targetId),
                any(Instant.class)
        )).thenReturn(false);
        when(violationReportRepository.save(any(ViolationReport.class)))
                .thenAnswer(invocation -> {
                    ViolationReport saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });
        when(violationReportMapper.toResponse(any(ViolationReport.class)))
                .thenReturn(new ViolationReportResponse());

        violationReportService.submitReport(request, reporterId);

        ArgumentCaptor<ViolationReport> saved = ArgumentCaptor.forClass(ViolationReport.class);
        verify(violationReportRepository).save(saved.capture());
        assertEquals(ViolationStatus.PENDING_REVIEW, saved.getValue().getStatus());
        assertEquals(ViolationTargetType.LESSON_BLOCK, saved.getValue().getTargetType());
        assertEquals(request.getDescription(), saved.getValue().getDescription());
    }

    @Test
    @Order(3)
    @DisplayName("UTCID03 (A) - REVIEW target is not supported here -> COMMON_BAD_REQUEST")
    void submitReport_UnsupportedTargetType_IsRejected() {
        ViolationReportRequest request = request(ViolationTargetType.REVIEW);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> violationReportService.submitReport(request, reporterId)
        );

        assertEquals(MessageCodes.COMMON_BAD_REQUEST, error.getMessageCode());
        verify(violationReportRepository, never()).save(any());
    }

    @Test
    @Order(4)
    @DisplayName("UTCID04 (A) - target does not exist -> COMMON_NOT_FOUND")
    void submitReport_TargetNotFound_IsRejected() {
        ViolationReportRequest request = request(ViolationTargetType.COURSE);
        when(entityManager.find(Course.class, targetId)).thenReturn(null);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> violationReportService.submitReport(request, reporterId)
        );

        assertEquals(MessageCodes.COMMON_NOT_FOUND, error.getMessageCode());
        verify(violationReportRepository, never()).save(any());
    }

    @Test
    @Order(5)
    @DisplayName("UTCID05 (A) - reporter account not found -> AUTH_UNAUTHORIZED")
    void submitReport_ReporterNotFound_IsRejected() {
        ViolationReportRequest request = request(ViolationTargetType.COURSE);
        when(entityManager.find(Course.class, targetId)).thenReturn(new Course());
        when(entityManager.find(AppUser.class, reporterId, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(null);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> violationReportService.submitReport(request, reporterId)
        );

        assertEquals(MessageCodes.AUTH_UNAUTHORIZED, error.getMessageCode());
        verify(violationReportRepository, never()).save(any());
    }

    @Test
    @Order(6)
    @DisplayName("UTCID06 (A) - same report inside the spam window -> MSG-REP-002")
    void submitReport_DuplicateInsideSpamWindow_IsRejected() {
        ViolationReportRequest request = request(ViolationTargetType.COURSE);
        AppUser reporter = AppUser.builder().id(reporterId).build();

        when(entityManager.find(Course.class, targetId)).thenReturn(new Course());
        when(entityManager.find(AppUser.class, reporterId, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(reporter);
        when(violationReportRepository.isDuplicateReport(
                eq(reporterId),
                eq(ViolationTargetType.COURSE),
                eq(targetId),
                any(Instant.class)
        )).thenReturn(true);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> violationReportService.submitReport(request, reporterId)
        );

        assertEquals(MessageCodes.MSG_REP_002, error.getMessageCode());
        verify(violationReportRepository, never()).save(any());
        verify(notificationService, never()).createNotificationForAdminRole(
                any(), any(), any(), any(), any());
    }
}
