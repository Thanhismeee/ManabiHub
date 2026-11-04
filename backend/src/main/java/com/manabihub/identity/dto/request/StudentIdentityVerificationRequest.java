package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record StudentIdentityVerificationRequest(
        @Size(max = 128) String providerSessionId,
        @Size(max = 128) String providerTransactionId,
        @NotEmpty @Size(max = 64) Map<String, Object> sdkResult
) {
}
