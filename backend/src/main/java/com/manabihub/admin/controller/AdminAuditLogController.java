package com.manabihub.admin.controller;

import com.manabihub.audit.dto.AuditLogDetailDto;
import com.manabihub.audit.dto.AuditLogDto;
import com.manabihub.audit.dto.AuditLogFilterDto;
import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ApiResponse<PageResponse<AuditLogDto>> getAuditLogs(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        AuditLogFilterDto filter = AuditLogFilterDto.builder()
                .actor(actor)
                .role(role)
                .targetType(targetType)
                .targetId(targetId)
                .action(action)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        return ApiResponse.success(auditLogService.getAuditLogs(filter, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuditLogDetailDto> getAuditLogDetail(@PathVariable UUID id) {
        return ApiResponse.success(auditLogService.getAuditLogDetail(id));
    }
}
