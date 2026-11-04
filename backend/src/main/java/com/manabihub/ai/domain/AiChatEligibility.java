package com.manabihub.ai.domain;

public record AiChatEligibility(
        boolean eligible,
        String messageCode,
        String message
) {

    public static AiChatEligibility available() {
        return new AiChatEligibility(true, null, "AI chat is available for this lesson.");
    }

    public static AiChatEligibility unavailable(String messageCode, String message) {
        return new AiChatEligibility(false, messageCode, message);
    }
}
