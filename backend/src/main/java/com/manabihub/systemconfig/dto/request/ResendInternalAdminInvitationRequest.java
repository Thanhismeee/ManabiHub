package com.manabihub.systemconfig.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendInternalAdminInvitationRequest(
        @NotBlank @Size(min = 5, max = 500) String reason
) {
}
