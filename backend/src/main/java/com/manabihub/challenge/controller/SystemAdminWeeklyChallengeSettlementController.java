package com.manabihub.challenge.controller;

import com.manabihub.challenge.service.ChallengeRewardSettlementService;
import com.manabihub.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Operational retry endpoint for a failed weekly settlement. */
@RestController
@RequestMapping("/api/v1/admin/system/weekly-challenge-settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemAdminWeeklyChallengeSettlementController {

    private final ChallengeRewardSettlementService settlementService;

    @PostMapping("/{challengeId}/retry")
    public ApiResponse<Void> retry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID challengeId
    ) {
        settlementService.retryWeeklySettlement(UUID.fromString(jwt.getSubject()), challengeId);
        return ApiResponse.success("COMMON_SUCCESS", "Đã yêu cầu chạy lại chốt thưởng tuần.", null);
    }
}
