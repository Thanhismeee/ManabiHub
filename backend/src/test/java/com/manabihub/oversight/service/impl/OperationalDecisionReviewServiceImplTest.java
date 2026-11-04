package com.manabihub.oversight.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.oversight.dto.request.DecisionWarningRequest;
import com.manabihub.oversight.dto.response.DecisionReviewDetailResponse;
import com.manabihub.oversight.entity.OperationalDecisionReview;
import com.manabihub.oversight.enums.DecisionReviewStatus;
import com.manabihub.oversight.enums.DecisionWarningLevel;
import com.manabihub.oversight.repository.OperationalDecisionReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalDecisionReviewServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private OperationalDecisionReviewRepository reviewRepository;
    @Mock private InternalAdminAccountRepository adminRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    private OperationalDecisionReviewServiceImpl service;
    private final UUID reviewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OperationalDecisionReviewServiceImpl(
                auditLogRepository,
                reviewRepository,
                adminRepository,
                currentUserService,
                notificationService,
                auditLogService,
                jdbcTemplate
        );
    }

    @Test
    void sendWarning_NotifiesOriginalManagerWithoutChangingOriginalDecision() {
        UUID actorId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        Map<String, Object> decisionAfter = new LinkedHashMap<>();
        decisionAfter.put("status", "APPROVED");
        decisionAfter.put("providerReference", null);
        AuditLog audit = AuditLog.builder()
                .id(UUID.randomUUID())
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(actorId)
                .actorRoleCode("FINANCE_MANAGER")
                .action("APPROVE_REFUND")
                .targetType("REFUND_REQUEST")
                .targetId(refundId)
                .beforeValue(Map.of("status", "PENDING"))
                .afterValue(decisionAfter)
                .createdAt(Instant.now())
                .build();
        InternalAdminAccount actor = new InternalAdminAccount();
        actor.setId(actorId);
        actor.setEmail("finance@example.com");
        actor.setFullName("Finance Manager");

        when(currentUserService.getCurrentUserId()).thenReturn(reviewerId);
        when(adminRepository.hasPermission(reviewerId, "OPERATIONAL_DECISION_WARNING_SEND")).thenReturn(true);
        when(auditLogRepository.findById(audit.getId())).thenReturn(Optional.of(audit));
        when(adminRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(reviewRepository.findByAuditLogIdForUpdate(audit.getId())).thenReturn(Optional.empty());
        when(reviewRepository.save(any(OperationalDecisionReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DecisionReviewDetailResponse result = service.sendWarning(
                audit.getId(),
                new DecisionWarningRequest(DecisionWarningLevel.HIGH, "Kiểm tra lại chứng từ provider")
        );

        assertEquals(DecisionReviewStatus.WARNING_SENT, result.reviewStatus());
        assertEquals("APPROVE_REFUND", audit.getAction());
        assertEquals("APPROVED", audit.getAfterValue().get("status"));
        verify(notificationService).createAdminNotificationOnce(
                "operational-warning:" + audit.getId(),
                actorId,
                "finance@example.com",
                "Cảnh báo hậu kiểm quyết định",
                "System Admin đã gửi cảnh báo mức HIGH cho quyết định APPROVE_REFUND. Ghi chú: Kiểm tra lại chứng từ provider",
                NotificationTypes.OPERATIONAL_DECISION_WARNING,
                "/admin/refunds/" + refundId
        );
        verify(auditLogService).logAdminAction(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void get_RejectsEventsNotDecidedByEitherManagerRole() {
        UUID auditId = UUID.randomUUID();
        AuditLog audit = AuditLog.builder()
                .id(auditId)
                .actorRoleCode("SYSTEM_ADMIN")
                .action("COURSE_APPROVED")
                .targetType("COURSE")
                .build();
        when(currentUserService.getCurrentUserId()).thenReturn(reviewerId);
        when(adminRepository.hasPermission(reviewerId, "OPERATIONAL_DECISION_REVIEW_VIEW")).thenReturn(true);
        when(auditLogRepository.findById(auditId)).thenReturn(Optional.of(audit));

        BusinessException error = assertThrows(BusinessException.class, () -> service.get(auditId));

        assertEquals(MessageCodes.ADMIN_PERMISSION_DENIED, error.getMessageCode());
    }
}
