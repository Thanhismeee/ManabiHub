package com.manabihub.audit.service.impl;

import com.manabihub.audit.dto.AuditLogDetailDto;
import com.manabihub.audit.dto.AuditLogFilterDto;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private InternalAdminAccountRepository internalAdminAccountRepository;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks private AuditLogServiceImpl auditLogService;

    @Test
    void getAuditLogDetailRedactsSensitiveValuesRecursively() {
        UUID auditLogId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
                .id(auditLogId)
                .actorType("SYSTEM")
                .action("ADMIN_ACCOUNT_UPDATED")
                .targetType("INTERNAL_ADMIN")
                .beforeValue(Map.of(
                        "displayName", "Original name",
                        "credentials", Map.of(
                                "password", "never-return-this",
                                "refreshToken", "never-return-this-either")))
                .afterValue(Map.of(
                        "items", List.of(Map.of(
                                "secretKey", "private",
                                "status", "ACTIVE"))))
                .metadata(Map.of(
                        "identityDocument", "raw-document",
                        "requestId", "req-123"))
                .build();
        when(auditLogRepository.findById(auditLogId)).thenReturn(Optional.of(auditLog));

        AuditLogDetailDto result = auditLogService.getAuditLogDetail(auditLogId);

        assertEquals("Original name", result.getBeforeValue().get("displayName"));

        @SuppressWarnings("unchecked")
        Map<String, Object> credentials =
                (Map<String, Object>) result.getBeforeValue().get("credentials");
        assertEquals("***REDACTED***", credentials.get("password"));
        assertEquals("***REDACTED***", credentials.get("refreshToken"));

        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) result.getAfterValue().get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstItem = (Map<String, Object>) items.get(0);
        assertEquals("***REDACTED***", firstItem.get("secretKey"));
        assertEquals("ACTIVE", firstItem.get("status"));

        assertEquals("***REDACTED***", result.getMetadata().get("identityDocument"));
        assertEquals("req-123", result.getMetadata().get("requestId"));
    }

    @Test
    void getAuditLogsRejectsReversedDateRange() {
        AuditLogFilterDto filter = AuditLogFilterDto.builder()
                .fromDate(Instant.parse("2026-08-02T00:00:00Z"))
                .toDate(Instant.parse("2026-08-01T00:00:00Z"))
                .build();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> auditLogService.getAuditLogs(filter, PageRequest.of(0, 20)));

        assertEquals("MSG-COM-002", exception.getMessageCode());
    }
}
