package com.manabihub.finance.dto.request;

import com.manabihub.finance.enums.ExpenseCategory;
import com.manabihub.finance.enums.ExpenseStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ExpenseFilterRequest {
    private ExpenseStatus status;
    private ExpenseCategory category;
    private String keyword;
    private String vendor;
    private String providerCode;
    private String invoiceNumber;
    private UUID createdBy;
    private BigDecimal minAmountVnd;
    private BigDecimal maxAmountVnd;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate incurredFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate incurredTo;
}
