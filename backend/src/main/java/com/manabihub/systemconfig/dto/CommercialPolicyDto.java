package com.manabihub.systemconfig.dto;

import com.manabihub.systemconfig.model.CommercialPolicy;

import java.math.BigDecimal;
import java.time.Instant;

public record CommercialPolicyDto(
        String currency,
        BigDecimal commissionRate,
        Integer refundWindowDays,
        Integer refundProgressLimitPercent,
        Integer escrowHoldingDays,
        BigDecimal payoutThreshold,
        BigDecimal withdrawalFee,
        Integer kycTargetDaysMin,
        Integer kycTargetDaysMax,
        String policyVersion,
        Instant effectiveAt
) {
    public static CommercialPolicyDto from(CommercialPolicy policy) {
        return new CommercialPolicyDto(
                policy.currency(),
                policy.commissionRate(),
                policy.refundWindowDays(),
                policy.refundProgressLimitPercent(),
                policy.escrowHoldingDays(),
                policy.payoutThreshold(),
                policy.withdrawalFee(),
                policy.kycTargetDaysMin(),
                policy.kycTargetDaysMax(),
                policy.policyVersion(),
                policy.effectiveAt());
    }
}
