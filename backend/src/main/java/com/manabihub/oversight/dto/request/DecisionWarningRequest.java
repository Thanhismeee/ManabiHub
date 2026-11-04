package com.manabihub.oversight.dto.request;

import com.manabihub.oversight.enums.DecisionWarningLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionWarningRequest(
        @NotNull DecisionWarningLevel level,
        @NotBlank @Size(max = 2000) String note
) {
}
