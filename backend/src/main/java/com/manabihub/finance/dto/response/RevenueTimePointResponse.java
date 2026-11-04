package com.manabihub.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueTimePointResponse(
        LocalDate bucket,
        BigDecimal grossSales,
        long successfulOrders,
        BigDecimal refundAmount,
        long refundCount,
        BigDecimal commissionRecognized,
        BigDecimal commissionReversed,
        BigDecimal platformRevenue,
        BigDecimal paymentFees,
        BigDecimal operatingExpenses
) {
}
