package com.manabihub.finance.dto.request;

import com.manabihub.finance.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ExpenseLineRequest(
        @NotNull ExpenseCategory categoryCode,
        @NotBlank @Size(max = 500) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal originalAmount
) {
}
