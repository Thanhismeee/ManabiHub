package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotAdminPasswordRequest(
        @NotBlank(message = "MSG-COM-002")
        @Email(message = "MSG-COM-002")
        @Size(max = 255, message = "MSG-COM-002")
        String email
) {
}
