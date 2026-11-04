package com.manabihub.oversight.dto.response;

import com.manabihub.oversight.enums.DecisionDomain;
import com.manabihub.oversight.enums.DecisionReviewStatus;
import com.manabihub.oversight.enums.DecisionWarningLevel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DecisionReviewDetailResponse(
        UUID auditLogId,
        DecisionDomain domain,
        String action,
        String targetType,
        UUID targetId,
        UUID decisionActorId,
        String decisionActorName,
        String decisionActorEmail,
        String decisionRole,
        Instant decisionAt,
        Map<String, Object> beforeValue,
        Map<String, Object> afterValue,
        Map<String, Object> metadata,
        DecisionReviewStatus reviewStatus,
        DecisionWarningLevel warningLevel,
        String reviewNote,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant warningSentAt
) {
}
