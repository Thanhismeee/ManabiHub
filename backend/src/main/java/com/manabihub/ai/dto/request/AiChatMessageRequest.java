package com.manabihub.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiChatMessageRequest(
        @NotBlank(message = "Question is required")
        @Size(max = 2000, message = "Question must not exceed 2000 characters")
        String question,
        
        List<AiChatHistoryItem> history
) {
}
