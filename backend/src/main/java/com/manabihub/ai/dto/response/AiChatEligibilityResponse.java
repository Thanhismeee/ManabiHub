package com.manabihub.ai.dto.response;

public record AiChatEligibilityResponse(
        boolean eligible,
        String unavailableCode,
        String message
) {
}
