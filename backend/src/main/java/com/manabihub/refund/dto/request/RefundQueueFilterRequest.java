package com.manabihub.refund.dto.request;

import com.manabihub.refund.enums.RefundStatus;
import com.manabihub.refund.enums.RefundProviderStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class RefundQueueFilterRequest {
    private UUID refundId;
    private RefundStatus status;
    private RefundProviderStatus providerStatus;
    private String orderCode;
    private String student;
    private UUID courseId;
    private String course;
    private String paymentProvider;
    private Boolean reconciliationRequired;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private UUID decidedBy;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant createdTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant decidedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant decidedTo;
}
