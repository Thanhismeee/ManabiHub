package com.manabihub.cohort.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCohortMemberRequest(
        @NotBlank @Email @Size(max = 255) String studentEmail
) {
}
