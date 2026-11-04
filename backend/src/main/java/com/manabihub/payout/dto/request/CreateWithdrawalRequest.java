package com.manabihub.payout.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWithdrawalRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String bankAccountId;

    @Valid
    private BankAccountDto bankAccount;

    @Pattern(regexp = "^\\d{6}$", message = "OTP code must contain exactly 6 digits")
    private String otpCode;

    private UUID phoneAuthChallengeId;

    @Size(max = 10_000, message = "Firebase ID token is too large")
    private String firebaseIdToken;

    @Builder.Default
    private boolean saveAccount = false;

    /**
     * Temporary local/test acknowledgement used until the real identity and
     * bank-account ownership provider is integrated. It is never trusted by
     * non-local verification implementations.
     */
    @Builder.Default
    private boolean ownershipConfirmed = false;

    /**
     * Bank QR image selected by the teacher for Finance to scan. This is a
     * data URL only at the transport boundary; the server decodes and stores
     * the bytes privately on the withdrawal request.
     */
    @Size(max = 3_000_000, message = "Bank QR image is too large")
    private String bankQrDataUrl;

    @AssertTrue(message = "Provide exactly one of bankAccountId or bankAccount")
    public boolean isBankAccountSelectionValid() {
        boolean hasSavedAccount = bankAccountId != null && !bankAccountId.isBlank();
        boolean hasNewAccount = bankAccount != null;
        return hasSavedAccount != hasNewAccount;
    }

    @AssertTrue(message = "Provide either an OTP code or a Firebase phone proof")
    public boolean isOtpProofValid() {
        boolean hasOtpCode = otpCode != null && !otpCode.isBlank();
        boolean hasFirebaseChallenge = phoneAuthChallengeId != null;
        boolean hasFirebaseToken = firebaseIdToken != null && !firebaseIdToken.isBlank();
        return hasOtpCode != (hasFirebaseChallenge && hasFirebaseToken)
                && hasFirebaseChallenge == hasFirebaseToken;
    }
}
