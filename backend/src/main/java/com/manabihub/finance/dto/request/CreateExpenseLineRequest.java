package com.manabihub.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateExpenseLineRequest {
    @NotBlank(message = "Category code is required")
    @Pattern(
            regexp = "^(INFRA_APP_COMPUTE|INFRA_DATABASE|INFRA_STORAGE)$",
            message = "Invalid category code"
    )
    private String categoryCode;
}
