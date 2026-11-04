package com.manabihub.kyc.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KycRequestResponse(
        UUID requestId,
        String status,
        String statusLabel,
        Instant submittedAt,
        String ekycProvider,
        String ekycReferenceId,
        String providerSessionId,
        String providerTransactionId,
        String identityStatus,
        String identityStatusLabel,
        Instant identityVerifiedAt,
        String certificateStatus,
        String certificateStatusLabel,
        Instant certificateSubmittedAt,
        String riskLevel,
        String certificateCode,
        boolean copyrightAgreed,
        Map<String, Object> verificationPayload,
        List<KycDocumentResponse> documents
) {
}
