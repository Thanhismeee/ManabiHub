package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetupAdminPasswordRequest(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Size(max = 72) String password
) {
}
