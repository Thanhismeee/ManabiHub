package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeAdminPasswordRequest(
        @NotBlank
        @Size(max = 72)
        String currentPassword,
        @NotBlank
        @Size(max = 72)
        String newPassword
) {
}
