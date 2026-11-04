package com.manabihub.oversight.dto.response;

import com.manabihub.oversight.enums.DecisionDomain;
import com.manabihub.oversight.enums.DecisionReviewStatus;
import com.manabihub.oversight.enums.DecisionWarningLevel;

import java.time.Instant;
import java.util.UUID;

public record DecisionReviewSummaryResponse(
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
        DecisionReviewStatus reviewStatus,
        DecisionWarningLevel warningLevel,
        Instant reviewedAt
) {
}
