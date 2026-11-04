package com.manabihub.moderation.dto.response;

import com.manabihub.moderation.enums.ViolationReportStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ViolationQueueItemResponse {
    private UUID reportId;
    private ViolationReportStatus status;
    private String targetType;
    private UUID targetId;
    private String reason;
    private String reporterName;
    private Instant submittedAt;
}
