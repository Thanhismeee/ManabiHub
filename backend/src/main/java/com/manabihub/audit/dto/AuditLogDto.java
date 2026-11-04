package com.manabihub.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
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
    private Instant createdAt;
}
