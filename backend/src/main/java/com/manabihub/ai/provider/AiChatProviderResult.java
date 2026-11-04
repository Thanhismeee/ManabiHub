package com.manabihub.ai.provider;

public record AiChatProviderResult(
        String answer,
        String provider,
        Integer inputTokens,
        Integer outputTokens
) {
}
