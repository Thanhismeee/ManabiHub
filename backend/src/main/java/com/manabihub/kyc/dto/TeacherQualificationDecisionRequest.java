package com.manabihub.kyc.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeacherQualificationDecisionRequest(
        @NotNull Boolean approved,
        @Size(max = 2000) String reviewNote
) {
}
