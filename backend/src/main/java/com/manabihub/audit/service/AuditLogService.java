package com.manabihub.audit.service;

import java.util.Map;
import java.util.UUID;

public interface AuditLogService {

    void logUserAction(
            UUID userId,
            String roleCode,
            String action,
            String targetType,
            UUID targetId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue,
            Map<String, Object> metadata
    );
    void logAdminAction(
            UUID adminId,
            String roleCode,
            String action,
            String targetType,
            UUID targetId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue,
            Map<String, Object> metadata
    );

    com.manabihub.common.response.PageResponse<com.manabihub.audit.dto.AuditLogDto> getAuditLogs(com.manabihub.audit.dto.AuditLogFilterDto filter, org.springframework.data.domain.Pageable pageable);
    com.manabihub.audit.dto.AuditLogDetailDto getAuditLogDetail(UUID id);
}
