package com.manabihub.challenge.controller;

import com.manabihub.challenge.dto.*;
import com.manabihub.challenge.service.WeeklyChallengeGameService;
import com.manabihub.challenge.service.WeeklyChallengeLeaderboardService;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.identity.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/weekly-challenge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentWeeklyChallengeController {
    private final WeeklyChallengeGameService service;
    private final WeeklyChallengeLeaderboardService leaderboardService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<WeeklyChallengeResponse> current() {
        return ApiResponse.success(service.current(currentUserService.getCurrentUserId()));
    }

    @PostMapping("/{challengeId}/attempts")
    public ApiResponse<ChallengeAttemptResponse> start(@PathVariable UUID challengeId) {
        return ApiResponse.success(service.start(currentUserService.getCurrentUserId(), challengeId));
    }

    @PostMapping("/attempts/{attemptId}/matches")
    public ApiResponse<ChallengeAttemptResponse> match(@PathVariable UUID attemptId,
                                                       @Valid @RequestBody MatchCardsRequest request) {
        return ApiResponse.success(service.match(currentUserService.getCurrentUserId(), attemptId, request));
    }

    @GetMapping("/{challengeId}/leaderboard")
    public ApiResponse<WeeklyChallengeLeaderboardResponse> leaderboard(@PathVariable UUID challengeId) {
        return ApiResponse.success(leaderboardService.forStudent(
                currentUserService.getCurrentUserId(), challengeId));
    }
}
