package com.manabihub.ai.dto.response;

import java.util.UUID;

public record AiChatMessageResponse(
        UUID courseId,
        UUID lessonBlockId,
        String answer,
        String disclaimerCode,
        String provider
) {
}
