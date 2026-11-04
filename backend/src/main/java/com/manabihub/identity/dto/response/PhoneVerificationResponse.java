package com.manabihub.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PhoneVerificationResponse(
        String phoneNumber,
        boolean verified,
        Instant verifiedAt,
        String verificationMethod,
        UUID challengeId,
        String phoneNumberE164,
        Instant expiresAt
) {

    public static PhoneVerificationResponse verified(String phoneNumber, Instant verifiedAt) {
        return new PhoneVerificationResponse(
                phoneNumber, true, verifiedAt, null, null, null, null);
    }
}
