package com.manabihub.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseOverviewResponse(
        LocalDate from,
        LocalDate to,
        long totalDocuments,
        BigDecimal totalConfirmedVnd,
        long draftCount,
        long confirmedCount,
        long paidCount,
        long overdueCount,
        Instant generatedAt
) {
}
