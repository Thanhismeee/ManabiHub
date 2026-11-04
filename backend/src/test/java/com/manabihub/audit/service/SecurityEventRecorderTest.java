package com.manabihub.audit.service;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

/**
 * MHB-023: cột actor_role_code cho phép rỗng, nên bỏ sót nó không sinh lỗi nào -
 * chỉ có test đọc lại đúng trường mới chặn được việc tái diễn.
 */
@ExtendWith(MockitoExtension.class)
class SecurityEventRecorderTest {

    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks private SecurityEventRecorder securityEventRecorder;

    @Captor private ArgumentCaptor<AuditLog> auditLogCaptor;

    @Test
    void recordAdminAccessDeniedStoresRoleCodeSoTheRoleFilterFindsTheEvent() {
        UUID adminId = UUID.randomUUID();

        securityEventRecorder.recordAdminAccessDenied(
                adminId,
                "COURSE_MANAGER",
                "ACCESS_DENIED",
                "ENDPOINT",
                Map.of("path", "/api/v1/admin/system-settings", "method", "GET"));

        verify(auditLogRepository).saveAndFlush(auditLogCaptor.capture());
        AuditLog saved = auditLogCaptor.getValue();

        assertEquals("INTERNAL_ADMIN", saved.getActorType());
        assertEquals(adminId, saved.getActorAdminId());
        assertNull(saved.getActorUserId());
        assertEquals("COURSE_MANAGER", saved.getActorRoleCode());
        assertEquals("ACCESS_DENIED", saved.getAction());
        assertEquals("ENDPOINT", saved.getTargetType());
    }

    /**
     * Nhánh người dùng thường cố ý không mang vai trò: MHB-53 chỉ yêu cầu vai trò
     * cho admin nội bộ. Ghim lại để quyết định phạm vi này nhìn thấy được, chứ
     * không trông như một chỗ sót thứ hai.
     */
    @Test
    void recordAccessDeniedKeepsThePublicUserShapeUnchanged() {
        UUID userId = UUID.randomUUID();

        securityEventRecorder.recordAccessDenied(
                userId, "ACCESS_DENIED", "COURSE_REVIEW", null, Map.of());

        verify(auditLogRepository).saveAndFlush(auditLogCaptor.capture());
        AuditLog saved = auditLogCaptor.getValue();

        assertEquals("USER", saved.getActorType());
        assertEquals(userId, saved.getActorUserId());
        assertNull(saved.getActorAdminId());
        assertNull(saved.getActorRoleCode());
    }

    @Test
    void unattributedDenialDoesNotInventAnActorId() {
        securityEventRecorder.recordUnattributedAccessDenied(
                "ACCESS_DENIED", "ENDPOINT", Map.of("path", "/api/v1/teacher/courses"));

        verify(auditLogRepository).saveAndFlush(auditLogCaptor.capture());
        AuditLog saved = auditLogCaptor.getValue();
        assertEquals("SYSTEM", saved.getActorType());
        assertNull(saved.getActorUserId());
        assertNull(saved.getActorAdminId());
        assertEquals("ACCESS_DENIED", saved.getAction());
    }
}
