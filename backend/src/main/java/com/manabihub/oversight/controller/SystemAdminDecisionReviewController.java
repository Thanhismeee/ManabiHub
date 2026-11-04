package com.manabihub.oversight.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.oversight.dto.request.DecisionReviewFilterRequest;
import com.manabihub.oversight.dto.request.DecisionWarningRequest;
import com.manabihub.oversight.dto.response.DecisionReviewDetailResponse;
import com.manabihub.oversight.dto.response.DecisionReviewSummaryResponse;
import com.manabihub.oversight.service.OperationalDecisionReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/decision-reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemAdminDecisionReviewController {

    private final OperationalDecisionReviewService reviewService;

    @GetMapping
    public ApiResponse<PageResponse<DecisionReviewSummaryResponse>> search(
            @ModelAttribute DecisionReviewFilterRequest filter,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(reviewService.search(filter, pageable));
    }

    @GetMapping("/{auditLogId}")
    public ApiResponse<DecisionReviewDetailResponse> get(@PathVariable UUID auditLogId) {
        return ApiResponse.success(reviewService.get(auditLogId));
    }

    @PostMapping("/{auditLogId}/reviewed")
    public ApiResponse<DecisionReviewDetailResponse> markReviewed(@PathVariable UUID auditLogId) {
        return ApiResponse.success(
                MessageCodes.ADMIN_ACTION_SUCCESS,
                "Decision marked as reviewed",
                reviewService.markReviewed(auditLogId));
    }

    @PostMapping("/{auditLogId}/warnings")
    public ApiResponse<DecisionReviewDetailResponse> sendWarning(
            @PathVariable UUID auditLogId,
            @Valid @RequestBody DecisionWarningRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.MSG_OVS_001,
                "Warning sent to the decision owner",
                reviewService.sendWarning(auditLogId, request));
        }
}
