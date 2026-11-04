package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetAdminPasswordRequest(
        @NotBlank
        @Size(max = 512)
        String token,
        @NotBlank
        @Size(max = 72)
        String password
) {
}
