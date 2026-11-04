package com.manabihub.moderation.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ViolationEvidenceResponse(
        UUID evidenceId,
        String evidenceType,
        String displayName,
        String accessUrl,
        String contentType,
        Instant submittedAt
) {
}
