package com.manabihub.writing.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AiWritingSuggestionResponse(
        UUID id,
        String status,
        JsonNode grammarSuggestions,
        JsonNode vocabularySuggestions,
        JsonNode structureSuggestions,
        String revisionGuidance,
        String confidenceLevel,
        boolean official,
        String failureReason,
        Instant createdAt
) {
}
