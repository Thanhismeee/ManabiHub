package com.manabihub.refund.dto.response;

import com.manabihub.refund.enums.RefundStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import com.manabihub.refund.dto.RefundEligibilitySnapshot;
import java.util.UUID;

@Data
@Builder
public class RefundDetailResponse {
    private UUID id;
    private UUID orderId;
    private String orderCode;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private RefundStatus status;
    private String reason;
    private RefundEligibilitySnapshot eligibilitySnapshot;
    private UUID orderItemId;
    private UUID courseId;
    private String courseTitle;
    private String currency;
    private BigDecimal grossAmount;
    private BigDecimal commissionAmount;
    private BigDecimal teacherNetAmount;
    private String paymentStatus;
    private String paymentProvider;
    private String paymentProviderTransactionId;
    private BigDecimal paymentAmount;
    private String escrowStatus;
    private BigDecimal escrowAmount;
    private Instant escrowReleaseAt;
    private String providerStatus;
    private String providerName;
    private String providerReference;
    private String providerResultCode;
    private Integer providerAttemptCount;
    private String reconciliationReasonCode;
    private int autoRefundAttemptCount;
    private Instant autoRefundNextAttemptAt;
    private String autoRefundLastErrorCode;
    private String decisionReasonCode;
    private UUID decidedBy;
    private String decisionNote;
    private Instant decidedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
