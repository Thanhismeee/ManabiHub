package com.manabihub.refund.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.refund.dto.request.RefundDecisionRequest;
import com.manabihub.refund.dto.request.RefundQueueFilterRequest;
import com.manabihub.refund.dto.response.RefundDetailResponse;
import com.manabihub.refund.dto.response.RefundQueueResponse;
import com.manabihub.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FINANCE_MANAGER')")
public class RefundAdminController {

    private final RefundService refundService;

    @GetMapping
    public ApiResponse<PageResponse<RefundQueueResponse>> searchRefunds(
            @ModelAttribute RefundQueueFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(refundService.searchRefunds(filter, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<RefundDetailResponse> getRefundDetail(@PathVariable UUID id) {
        return ApiResponse.success(refundService.getRefundDetail(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approveRefund(@PathVariable UUID id, @Valid @RequestBody RefundDecisionRequest request) {
        refundService.approveRefund(id, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> rejectRefund(@PathVariable UUID id, @Valid @RequestBody RefundDecisionRequest request) {
        refundService.rejectRefund(id, request);
        return ApiResponse.success(null);
    }
}
