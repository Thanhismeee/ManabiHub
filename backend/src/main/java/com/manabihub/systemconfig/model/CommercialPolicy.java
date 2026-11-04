package com.manabihub.systemconfig.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CommercialPolicy(
        String currency,
        BigDecimal commissionRate,
        int refundWindowDays,
        int refundProgressLimitPercent,
        int escrowHoldingDays,
        BigDecimal payoutThreshold,
        BigDecimal withdrawalFee,
        int kycTargetDaysMin,
        int kycTargetDaysMax,
        String policyVersion,
        Instant effectiveAt
) {
}
