package com.manabihub.ai.controller;

import com.manabihub.ai.dto.request.AiChatMessageRequest;
import com.manabihub.ai.dto.response.AiChatEligibilityResponse;
import com.manabihub.ai.dto.response.AiChatMessageResponse;
import com.manabihub.ai.service.AiChatService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/student/courses/{courseId}/lesson-blocks/{lessonBlockId}/ai-chat")
@PreAuthorize("hasRole('STUDENT')")
public class StudentAiChatController {

    private final AiChatService aiChatService;

    @GetMapping("/eligibility")
    public ResponseEntity<ApiResponse<AiChatEligibilityResponse>> getEligibility(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonBlockId
    ) {
        AiChatEligibilityResponse response = aiChatService.getEligibility(courseId, lessonBlockId);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "AI chat eligibility loaded.",
                response
        ));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<AiChatMessageResponse>> sendMessage(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonBlockId,
            @Valid @RequestBody AiChatMessageRequest request
    ) {
        AiChatMessageResponse response = aiChatService.sendMessage(courseId, lessonBlockId, request);
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "AI chat response generated.",
                response
        ));
    }
}
