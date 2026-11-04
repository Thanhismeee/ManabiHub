package com.manabihub.systemconfig.dto.request;

import com.manabihub.identity.enums.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateInternalAdminStatusRequest(
        @NotNull
        AccountStatus status,

        @NotBlank
        @Size(min = 5, max = 500)
        String reason
) {
}
