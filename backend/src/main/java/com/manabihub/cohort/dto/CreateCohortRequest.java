package com.manabihub.cohort.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCohortRequest(
        @NotNull UUID courseId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 32) String code,
        @Size(max = 160) String organizationName,
        @Size(max = 160) String branchName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Min(1) @Max(500) Integer maxStudents
) {
}
