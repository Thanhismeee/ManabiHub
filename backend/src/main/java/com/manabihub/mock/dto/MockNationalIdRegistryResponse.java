package com.manabihub.mock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record MockNationalIdRegistryResponse(
        UUID id,
        String idNumber,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String permanentAddress,
        LocalDate issueDate,
        LocalDate expiryDate,
        String issuePlace,
        String documentStatus,
        String frontBackMatchStatus,
        String cornerBlurStatus,
        String idQualityStatus,
        String issueDateStatus,
        String expiryStatus,
        String documentIdentificationStatus,
        String warningStatus,
        String overlayImageStatus,
        String openEyesStatus,
        String blurredFaceStatus,
        String faceValidationStatus,
        String coveredFaceStatus,
        BigDecimal faceMatchingScore,
        String sourceProvider,
        String sourceReference,
        Map<String, Object> rawPayload
) {
}
