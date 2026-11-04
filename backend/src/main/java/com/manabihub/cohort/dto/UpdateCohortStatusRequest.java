package com.manabihub.cohort.dto;

import com.manabihub.cohort.domain.CohortStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCohortStatusRequest(@NotNull CohortStatus status) {
}
