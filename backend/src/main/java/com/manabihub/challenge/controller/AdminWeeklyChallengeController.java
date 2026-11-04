package com.manabihub.challenge.controller;

import com.manabihub.challenge.dto.*;
import com.manabihub.challenge.service.WeeklyChallengeLeaderboardService;
import com.manabihub.challenge.service.WeeklyChallengeManagementService;
import com.manabihub.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/weekly-challenges")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COURSE_MANAGER')")
public class AdminWeeklyChallengeController {
    private final WeeklyChallengeManagementService service;
    private final WeeklyChallengeLeaderboardService leaderboardService;

    @GetMapping
    public ApiResponse<List<WeeklyChallengeResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping
    public ApiResponse<WeeklyChallengeResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody UpsertWeeklyChallengeRequest request) {
        return ApiResponse.success(service.create(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WeeklyChallengeResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                       @Valid @RequestBody UpsertWeeklyChallengeRequest request) {
        return ApiResponse.success(service.update(UUID.fromString(jwt.getSubject()), id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<WeeklyChallengeResponse> publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.success(service.publish(UUID.fromString(jwt.getSubject()), id));
    }

    @PostMapping("/{id}/unpublish")
    public ApiResponse<WeeklyChallengeResponse> unpublish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.success(service.unpublish(UUID.fromString(jwt.getSubject()), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(UUID.fromString(jwt.getSubject()), id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/leaderboard")
    public ApiResponse<WeeklyChallengeLeaderboardResponse> leaderboard(@AuthenticationPrincipal Jwt jwt,
                                                                       @PathVariable UUID id) {
        return ApiResponse.success(leaderboardService.forCourseManager(
                UUID.fromString(jwt.getSubject()), id));
    }
}
