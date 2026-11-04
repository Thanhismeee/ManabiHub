package com.manabihub.finance.dto.response;

import com.manabihub.finance.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseLineResponse(
        UUID id,
        ExpenseCategory categoryCode,
        String description,
        BigDecimal originalAmount,
        BigDecimal amountVnd,
        int lineOrder
) {
}
