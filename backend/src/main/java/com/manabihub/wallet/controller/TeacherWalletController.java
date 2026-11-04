package com.manabihub.wallet.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.wallet.dto.request.WalletTransactionFilterRequest;
import com.manabihub.wallet.dto.response.TeacherWalletResponse;
import com.manabihub.wallet.dto.response.TeacherRevenueSummaryResponse;
import com.manabihub.wallet.dto.response.WalletTransactionDetailResponse;
import com.manabihub.wallet.dto.response.WalletTransactionResponse;
import com.manabihub.wallet.service.EscrowService;
import com.manabihub.wallet.service.WalletService;
import com.manabihub.wallet.service.WalletTransactionService;
import com.manabihub.wallet.service.TeacherRevenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher/wallet")
@RequiredArgsConstructor
@Tag(name = "Teacher Wallet", description = "Teacher Revenue Wallet Operations")
public class TeacherWalletController {

    private final WalletService walletService;
    private final EscrowService escrowService;
    private final WalletTransactionService walletTransactionService;
    private final TeacherRevenueService teacherRevenueService;

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get teacher wallet details")
    public ApiResponse<TeacherWalletResponse> getWallet(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        TeacherWalletResponse response = walletService.getTeacherWalletByUserId(userId);
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", response);
    }

    @GetMapping("/revenue-summary")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get reconciled teacher revenue by course")
    public ApiResponse<TeacherRevenueSummaryResponse> getRevenueSummary(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Revenue summary retrieved successfully.",
                teacherRevenueService.getRevenueSummary(userId));
    }

    @GetMapping("/escrow")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get teacher escrow ledger history")
    public ApiResponse<org.springframework.data.domain.Page<com.manabihub.wallet.dto.response.EscrowLedgerResponse>> getEscrowLedger(
            Authentication authentication,
            org.springframework.data.domain.Pageable pageable) {
        UUID userId = UUID.fromString(authentication.getName());
        org.springframework.data.domain.Page<com.manabihub.wallet.dto.response.EscrowLedgerResponse> response = escrowService.getTeacherEscrowLedgerByUserId(userId, pageable);
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", response);
    }

    /**
     * UC-17 step 3/6: paginated revenue-wallet transaction history (escrow credits, releases,
     * withdrawal reservations and payouts), optionally filtered.
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Lịch sử giao dịch ví doanh thu của giảng viên")
    public ApiResponse<PageResponse<WalletTransactionResponse>> getTransactions(
            Authentication authentication,
            WalletTransactionFilterRequest filter,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Success",
                walletTransactionService.getTeacherTransactions(userId, filter, pageable));
    }

    /** UC-17 alternative flow 6a: detail of one transaction with its escrow/payout reference. */
    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Chi tiết một giao dịch ví doanh thu")
    public ApiResponse<WalletTransactionDetailResponse> getTransactionDetail(
            Authentication authentication,
            @PathVariable UUID transactionId) {

        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Success",
                walletTransactionService.getTeacherTransactionDetail(userId, transactionId));
    }
}
