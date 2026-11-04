package com.manabihub.wallet.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reconciled teacher revenue view.  Held money remains owned by the platform
 * until escrow release; only settled/available wallet funds can be withdrawn.
 */
@Value
@Builder
public class TeacherRevenueSummaryResponse {
    BigDecimal totalGrossRevenue;
    BigDecimal totalTeacherNetRevenue;
    BigDecimal settledRevenue;
    BigDecimal heldInEscrow;
    BigDecimal availableInWallet;
    BigDecimal reservedForWithdrawal;
    BigDecimal totalWithdrawn;
    long totalSales;
    long totalRefundedSales;
    List<TeacherCourseRevenueResponse> courseRevenue;
}
