package com.manabihub.ai.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class AiChatGuardrail {

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "ignore previous instructions",
            "reveal system prompt",
            "show system prompt",
            "bypass safety",
            "jailbreak"
    );

    public boolean blocks(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return BLOCKED_PATTERNS.stream().anyMatch(normalized::contains);
    }
}
