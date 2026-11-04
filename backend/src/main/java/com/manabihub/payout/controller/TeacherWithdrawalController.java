package com.manabihub.payout.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.dto.response.WithdrawalRequestResponse;
import com.manabihub.payout.dto.response.WithdrawalOtpResponse;
import com.manabihub.payout.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teacher/withdrawals")
@RequiredArgsConstructor
@Tag(name = "Teacher Payout", description = "Teacher Revenue Withdrawal Operations")
public class TeacherWithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Create a withdrawal request", description = "Creates a withdrawal request with PENDING status. No bank transfer is executed by this endpoint.")
    public ApiResponse<WithdrawalRequestResponse> createWithdrawal(
            @Valid @RequestBody CreateWithdrawalRequest request,
            Authentication authentication) {
        String teacherId = authentication.getName();
        WithdrawalRequestResponse response = withdrawalService.createWithdrawalRequest(teacherId, request);
        return ApiResponse.success(MessageCodes.PAYOUT_WITHDRAWAL_REQUEST_CREATED, "Withdrawal request created", response);
    }

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "List teacher withdrawal requests")
    public ApiResponse<Page<WithdrawalRequestResponse>> listWithdrawals(
            Authentication authentication,
            Pageable pageable) {
        String teacherId = authentication.getName();
        Page<WithdrawalRequestResponse> responses = withdrawalService.getTeacherWithdrawals(teacherId, pageable);
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get teacher withdrawal detail")
    public ApiResponse<WithdrawalRequestResponse> getWithdrawalDetail(
            Authentication authentication,
            @PathVariable String id) {
        String teacherId = authentication.getName();
        WithdrawalRequestResponse response = withdrawalService.getWithdrawalDetail(teacherId, id);
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", response);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Cancel a pending withdrawal request")
    public ApiResponse<Void> cancelWithdrawal(
            Authentication authentication,
            @PathVariable String id) {
        String teacherId = authentication.getName();
        withdrawalService.cancelWithdrawal(teacherId, id);
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Successfully cancelled withdrawal", null);
    }

    @PostMapping("/send-otp")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Send OTP for withdrawal")
    public ApiResponse<WithdrawalOtpResponse> sendOtp(Authentication authentication) {
        String teacherId = authentication.getName();
        WithdrawalOtpResponse response = withdrawalService.sendWithdrawalOtp(teacherId);
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "OTP challenge created", response);
    }

    @GetMapping("/bank-accounts")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get saved bank accounts")
    public ApiResponse<java.util.List<com.manabihub.payout.dto.response.TeacherBankAccountResponse>> getSavedBankAccounts(Authentication authentication) {
        String teacherId = authentication.getName();
        java.util.List<com.manabihub.payout.dto.response.TeacherBankAccountResponse> accounts = withdrawalService.getSavedBankAccounts(teacherId);
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", accounts);
    }
}
