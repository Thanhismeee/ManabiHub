package com.manabihub.systemconfig.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSystemSettingRequest(
        @NotBlank
        @Size(max = 10_000)
        String value,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
