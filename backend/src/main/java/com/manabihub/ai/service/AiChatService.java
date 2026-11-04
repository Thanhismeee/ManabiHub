package com.manabihub.ai.service;

import com.manabihub.ai.dto.request.AiChatMessageRequest;
import com.manabihub.ai.dto.response.AiChatEligibilityResponse;
import com.manabihub.ai.dto.response.AiChatMessageResponse;

import java.util.UUID;

public interface AiChatService {

    AiChatEligibilityResponse getEligibility(UUID courseId, UUID lessonBlockId);

    AiChatMessageResponse sendMessage(UUID courseId, UUID lessonBlockId, AiChatMessageRequest request);
}
