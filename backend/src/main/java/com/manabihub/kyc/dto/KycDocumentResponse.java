package com.manabihub.kyc.dto;

import java.time.Instant;
import java.util.UUID;

public record KycDocumentResponse(
        UUID id,
        String documentType,
        String fileName,
        String mimeType,
        long fileSize,
        String fileHash,
        Instant createdAt
) {
}
