package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConfirmPhoneVerificationRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$", message = "MSG-PRO-002")
        String phoneNumber,
        @Pattern(regexp = "^\\d{6}$", message = "PHONE_VERIFICATION_INVALID_OTP")
        String code,
        UUID challengeId,
        @Size(max = 10_000, message = "PHONE_VERIFICATION_INVALID_OTP")
        String firebaseIdToken
) {

    @jakarta.validation.constraints.AssertTrue(message = "PHONE_VERIFICATION_INVALID_OTP")
    public boolean isVerificationProofValid() {
        boolean hasSmsCode = code != null && !code.isBlank();
        boolean hasFirebaseChallenge = challengeId != null;
        boolean hasFirebaseToken = firebaseIdToken != null && !firebaseIdToken.isBlank();
        return hasSmsCode != (hasFirebaseChallenge && hasFirebaseToken)
                && hasFirebaseChallenge == hasFirebaseToken;
    }
}
