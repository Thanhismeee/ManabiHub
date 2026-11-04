package com.manabihub.violation.dto;

import com.manabihub.violation.enums.ViolationStatus;
import com.manabihub.violation.enums.ViolationTargetType;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ViolationReportResponse {
    private UUID id;
    private UUID reporterId;
    private ViolationTargetType targetType;
    private UUID targetId;
    private String reason;
    private String description;
    private ViolationStatus status;
    private Instant createdAt;
}
