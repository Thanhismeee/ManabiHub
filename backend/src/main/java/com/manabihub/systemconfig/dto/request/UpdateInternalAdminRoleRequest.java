package com.manabihub.systemconfig.dto.request;

import com.manabihub.identity.enums.RoleCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateInternalAdminRoleRequest(
        @NotNull
        RoleCode roleCode,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
