package com.manabihub.kyc.port;

import java.time.Instant;

/**
 * Result of a VNPT server-to-server verification call.
 */
public record VnptServerVerificationResult(
        String confirmedTransactionId,
        String confirmedSessionId,
        boolean verified,
        String providerStatus,
        Instant providerVerifiedAt,
        String reasonCode,
        String serverIdNumber,
        String serverFullName,
        String serverDateOfBirth,
        String maskedReference
) {
    public static VnptServerVerificationResult success(
            String txId,
            String sessionId,
            String providerStatus,
            Instant verifiedAt,
            String idNumber,
            String fullName,
            String dateOfBirth,
            String maskedReference
    ) {
        return new VnptServerVerificationResult(
                txId, sessionId, true, providerStatus, verifiedAt,
                null, idNumber, fullName, dateOfBirth, maskedReference
        );
    }

    public static VnptServerVerificationResult failure(
            String txId,
            String sessionId,
            String providerStatus,
            String reasonCode
    ) {
        return new VnptServerVerificationResult(
                txId, sessionId, false, providerStatus, null,
                reasonCode, null, null, null, null
        );
    }
}
