package com.manabihub.refund.dto.response;

import com.manabihub.refund.enums.RefundStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RefundQueueResponse {
    private UUID id;
    private UUID orderId;
    private String orderCode;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private String reason;
    private RefundStatus status;
    private UUID orderItemId;
    private UUID courseId;
    private String courseTitle;
    private String currency;
    private BigDecimal grossAmount;
    private String paymentStatus;
    private String paymentProvider;
    private BigDecimal paymentAmount;
    private String providerStatus;
    private String reconciliationReasonCode;
    private UUID decidedBy;
    private Instant decidedAt;
    private Instant createdAt;
}
