package com.manabihub.payout.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.dto.response.StudentBankAccountResponse;
import com.manabihub.payout.dto.response.WithdrawalRequestResponse;
import com.manabihub.payout.dto.response.WithdrawalOtpResponse;
import com.manabihub.payout.service.StudentWithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentWithdrawalController {
    private final StudentWithdrawalService withdrawalService;

    @PostMapping
    public ApiResponse<WithdrawalRequestResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWithdrawalRequest request
    ) {
        return ApiResponse.success(
                MessageCodes.MSG_WALLET_002,
                "Yêu cầu rút tiền đã được tạo.",
                withdrawalService.createWithdrawal(userId(jwt), request));
    }

    @GetMapping
    public ApiResponse<Page<WithdrawalRequestResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable
    ) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Withdrawals retrieved successfully",
                withdrawalService.getMyWithdrawals(userId(jwt), pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<WithdrawalRequestResponse> detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Withdrawal retrieved successfully",
                withdrawalService.getMyWithdrawal(userId(jwt), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<WithdrawalRequestResponse> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Withdrawal cancelled successfully",
                withdrawalService.cancelWithdrawal(userId(jwt), id));
    }

    @PostMapping("/send-otp")
    public ApiResponse<WithdrawalOtpResponse> sendOtp(@AuthenticationPrincipal Jwt jwt) {
        WithdrawalOtpResponse response = withdrawalService.sendOtp(userId(jwt));
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "OTP challenge created", response);
    }

    @GetMapping("/bank-accounts")
    public ApiResponse<List<StudentBankAccountResponse>> bankAccounts(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Bank accounts retrieved successfully",
                withdrawalService.getSavedBankAccounts(userId(jwt)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
