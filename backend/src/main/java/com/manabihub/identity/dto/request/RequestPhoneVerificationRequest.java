package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RequestPhoneVerificationRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$", message = "MSG-PRO-002")
        String phoneNumber
) {
}
