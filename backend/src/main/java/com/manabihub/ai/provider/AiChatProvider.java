package com.manabihub.ai.provider;

import com.manabihub.ai.domain.AiChatContext;

import com.manabihub.ai.dto.request.AiChatHistoryItem;
import java.util.List;

public interface AiChatProvider {

    AiChatProviderResult generate(AiChatContext context, String question, List<AiChatHistoryItem> history);
}
