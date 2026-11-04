package com.manabihub.payout.service;

import java.time.LocalDateTime;

/**
 * Boundary for student identity and bank-account ownership verification.
 * The local/test implementation is an explicit simulation; production stays
 * fail-closed until a real provider is configured.
 */
public interface StudentBankOwnershipVerificationService {

    VerificationEvidence verify(boolean ownershipConfirmed);

    record VerificationEvidence(
            boolean verified,
            String method,
            LocalDateTime verifiedAt
    ) {
    }
}
