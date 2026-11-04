package com.manabihub.learning.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.identity.service.LearningSessionLeaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/learning-lease")
@RequiredArgsConstructor
public class LearningSessionController {

    private final LearningSessionLeaseService leaseService;

    @PostMapping("/acquire/{courseId}")
    public ApiResponse<Void> acquireLease(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        
        leaseService.acquireLease(userId, sessionId, courseId);
        return ApiResponse.success(null);
    }
}
