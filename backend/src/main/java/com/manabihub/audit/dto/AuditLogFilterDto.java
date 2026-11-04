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
public class AuditLogFilterDto {
    private String actor;
    private String role;
    private String targetType;
    private UUID targetId;
    private String action;
    private Instant fromDate;
    private Instant toDate;
}
