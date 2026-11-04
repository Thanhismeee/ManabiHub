package com.manabihub.kyc.domain;

public enum IdentityVerificationStatus {
    NOT_STARTED,
    PROCESSING,
    PENDING_SERVER_VERIFICATION,
    VERIFIED,
    FAILED
}
