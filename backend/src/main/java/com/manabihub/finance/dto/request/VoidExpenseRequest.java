package com.manabihub.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidExpenseRequest(
        @Size(max = 2000) String reason
) {
}
