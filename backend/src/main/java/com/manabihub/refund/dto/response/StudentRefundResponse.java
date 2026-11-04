package com.manabihub.refund.dto.response;

import com.manabihub.refund.dto.RefundEligibilitySnapshot;
import com.manabihub.refund.enums.RefundStatus;
import com.manabihub.refund.enums.StudentRefundType;

import java.time.Instant;
import java.util.UUID;

public record StudentRefundResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        UUID orderItemId,
        UUID courseId,
        String courseTitle,
        RefundStatus status,
        StudentRefundType refundType,
        String reason,
        RefundEligibilitySnapshot eligibilitySnapshot,
        String decisionReasonCode,
        String decisionNote,
        Instant decidedAt,
        boolean cancellable,
        Instant createdAt,
        Instant updatedAt,
        boolean automaticRefundPending,
        String reconciliationReasonCode
) {
}
