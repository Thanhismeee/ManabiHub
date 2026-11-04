package com.manabihub.refund.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.refund.dto.request.CreateStudentRefundRequest;
import com.manabihub.refund.dto.response.StudentRefundResponse;
import com.manabihub.refund.service.StudentRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentRefundController {

    private final StudentRefundService studentRefundService;
    private final CurrentUserService currentUserService;

    @PostMapping
    public ApiResponse<StudentRefundResponse> create(
            @Valid @RequestBody CreateStudentRefundRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.REFUND_REQUESTED,
                "Refund request submitted for Finance review.",
                studentRefundService.createRefundRequest(currentUserService.getCurrentUserId(), request)
        );
    }

    @GetMapping
    public ApiResponse<PageResponse<StudentRefundResponse>> listMine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.success(
                studentRefundService.getMyRefundRequests(currentUserService.getCurrentUserId(), pageable)
        );
    }

    @GetMapping("/{refundId}")
    public ApiResponse<StudentRefundResponse> detail(@PathVariable UUID refundId) {
        return ApiResponse.success(
                studentRefundService.getMyRefundDetail(currentUserService.getCurrentUserId(), refundId)
        );
    }

    @PostMapping("/{refundId}/cancel")
    public ApiResponse<StudentRefundResponse> cancel(@PathVariable UUID refundId) {
        return ApiResponse.success(
                MessageCodes.COMMON_UPDATED,
                "Pending refund request cancelled.",
                studentRefundService.cancelRefundRequest(currentUserService.getCurrentUserId(), refundId)
        );
    }
}
