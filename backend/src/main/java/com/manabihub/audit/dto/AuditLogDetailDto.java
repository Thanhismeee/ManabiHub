package com.manabihub.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDetailDto {
    private UUID id;
    private String actorType;
    private UUID actorUserId;
    private UUID actorAdminId;
    private String actorDisplayName;
    private String actorEmail;
    private String actorRoleCode;
    private String action;
    private String targetType;
    private UUID targetId;
    private Map<String, Object> beforeValue;
    private Map<String, Object> afterValue;
    private Map<String, Object> metadata;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
}
