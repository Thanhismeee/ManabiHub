package com.manabihub.systemconfig.dto.request;

import com.manabihub.identity.enums.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteInternalAdminRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 2, max = 255) String fullName,
        @NotNull RoleCode roleCode,
        @NotBlank @Size(min = 5, max = 500) String reason
) {
}
