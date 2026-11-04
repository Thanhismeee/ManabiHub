package com.manabihub.moderation.dto.response;

import com.manabihub.moderation.enums.ModerationActionType;
import com.manabihub.moderation.enums.ViolationReportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ViolationDetailResponse {
    private UUID reportId;
    private ViolationReportStatus status;
    private String reason;
    private String description;
    private Instant submittedAt;

    private ReporterSummary reporter;
    private ViolationTarget target;
    private List<ViolationEvidenceResponse> evidence;
    private List<ModerationHistoryItem> moderationHistory;

    private int previousWarnings;
    private int paidEnrollmentCount;
    private List<ModerationActionType> availableActions;
    private boolean severeActionAllowed;

    @Data
    @Builder
    public static class ReporterSummary {
        private UUID reporterId;
        private String displayName;
        private String role;
        private String accountAge;
    }

    @Data
    @Builder
    public static class ViolationTarget {
        private String targetType;
        private UUID targetId;
        private UUID courseId;
        private String courseTitle;
        private String currentStatus;
        private UUID affectedTeacherProfileId;
        private UUID affectedUserId;
        private String affectedUserName;
        private String contentTitle;
    }

    @Data
    @Builder
    public static class ModerationHistoryItem {
        private UUID decisionId;
        private String decisionType;
        private String decisionNote;
        private Instant decidedAt;
        private String decidedBy;
        private String evidenceRequestedFrom;
        private List<String> actions;
    }
}
