package com.manabihub.finance.dto.response;

import com.manabihub.finance.enums.ExpenseSourceType;
import com.manabihub.finance.enums.ExpenseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExpenseDetailResponse(
        UUID id,
        String expenseCode,
        String vendorName,
        String providerCode,
        String invoiceNumber,
        String description,
        String currency,
        BigDecimal exchangeRate,
        BigDecimal originalTotal,
        BigDecimal totalAmountVnd,
        LocalDate incurredAt,
        LocalDate dueDate,
        LocalDate exchangeRateDate,
        String exchangeRateSource,
        LocalDate billingPeriodFrom,
        LocalDate billingPeriodTo,
        Instant paidAt,
        UUID paidBy,
        String evidenceReference,
        ExpenseStatus status,
        ExpenseSourceType sourceType,
        UUID createdBy,
        UUID confirmedBy,
        Instant confirmedAt,
        UUID voidedBy,
        Instant voidedAt,
        String voidReason,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<ExpenseLineResponse> lines
) {
}
