package com.manabihub.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChallengePairRequest(
        @NotBlank @Size(max = 120) String prompt,
        @NotBlank @Size(max = 240) String answer
) {}
