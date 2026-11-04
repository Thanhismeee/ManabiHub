package com.manabihub.finance.dto.response;

import com.manabihub.finance.enums.ExpenseSourceType;
import com.manabihub.finance.enums.ExpenseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseSummaryResponse(
        UUID id,
        String expenseCode,
        String vendorName,
        String providerCode,
        String invoiceNumber,
        String currency,
        BigDecimal originalTotal,
        BigDecimal totalAmountVnd,
        LocalDate incurredAt,
        LocalDate dueDate,
        ExpenseStatus status,
        ExpenseSourceType sourceType,
        int lineCount,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
