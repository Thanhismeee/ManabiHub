package com.manabihub.finance.dto.response;

import java.math.BigDecimal;

public record RevenueSummaryResponse(
        BigDecimal grossSales,
        long successfulOrders,
        BigDecimal refundAmount,
        long refundCount,
        BigDecimal refundRate,
        BigDecimal netCollected,
        BigDecimal commissionRecognized,
        BigDecimal commissionReversed,
        BigDecimal platformRevenue,
        BigDecimal paymentFees,
        BigDecimal operatingExpenses,
        BigDecimal totalActualExpenses,
        BigDecimal netOperatingResult
) {
}
