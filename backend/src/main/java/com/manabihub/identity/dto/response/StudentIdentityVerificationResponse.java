package com.manabihub.identity.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record StudentIdentityVerificationResponse(
        boolean verified,
        String status,
        String provider,
        String maskedIdNumber,
        String fullName,
        LocalDate dateOfBirth,
        Instant verifiedAt
) {
}
