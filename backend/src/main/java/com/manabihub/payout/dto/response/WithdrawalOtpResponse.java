package com.manabihub.payout.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WithdrawalOtpResponse(
        String verificationMethod,
        UUID challengeId,
        String phoneNumberE164,
        String maskedDestination,
        Instant expiresAt
) {
}
