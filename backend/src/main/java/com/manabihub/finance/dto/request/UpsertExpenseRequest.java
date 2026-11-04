package com.manabihub.finance.dto.request;

import com.manabihub.finance.enums.ExpenseSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class UpsertExpenseRequest {

    private Long version;

    @NotBlank
    @Size(max = 255)
    private String vendorName;

    @Size(max = 80)
    private String providerCode;

    @Size(max = 120)
    private String invoiceNumber;

    @Size(max = 2000)
    private String description;

    @NotBlank
    @Pattern(regexp = "[A-Za-z]{3,10}")
    private String currency = "VND";

    @NotNull
    @DecimalMin(value = "0.000001")
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @NotNull
    private LocalDate incurredAt;

    private LocalDate dueDate;

    private LocalDate exchangeRateDate;

    @Size(max = 120)
    private String exchangeRateSource;

    private LocalDate billingPeriodFrom;
    private LocalDate billingPeriodTo;

    @Size(max = 500)
    private String evidenceReference;

    @NotNull
    private ExpenseSourceType sourceType = ExpenseSourceType.MANUAL_INVOICE;

    @Valid
    @NotEmpty
    @Size(max = 100)
    private List<ExpenseLineRequest> lines;
}
