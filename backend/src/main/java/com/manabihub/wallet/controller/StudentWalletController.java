package com.manabihub.wallet.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.wallet.dto.request.WalletTransactionFilterRequest;
import com.manabihub.wallet.dto.response.StudentWalletResponse;
import com.manabihub.wallet.dto.response.WalletTransactionDetailResponse;
import com.manabihub.wallet.dto.response.WalletTransactionResponse;
import com.manabihub.wallet.service.StudentWalletService;
import com.manabihub.wallet.service.WalletTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Student money-wallet endpoints: view balance and inspect the wallet ledger.
 *
 * Direct wallet top-up is intentionally not exposed. Student balances are credited by
 * refund workflows; existing legacy top-up rows remain readable for audit/history only.
 */
@RestController
@RequestMapping("/api/v1/student/wallet")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentWalletController {

    private final StudentWalletService studentWalletService;
    private final WalletTransactionService walletTransactionService;

    @GetMapping
    public ApiResponse<StudentWalletResponse> getWallet(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Wallet retrieved successfully.",
                studentWalletService.getWalletOverview(userId));
    }

    /**
     * UC-17 step 3/6: paginated transaction history, optionally filtered by type, direction,
     * date range and order/reference code. Scoped to the caller's own wallet.
     */
    @GetMapping("/transactions")
    @Operation(summary = "Lịch sử giao dịch ví của học viên")
    public ApiResponse<PageResponse<WalletTransactionResponse>> getTransactions(
            @AuthenticationPrincipal Jwt jwt,
            WalletTransactionFilterRequest filter,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Wallet transactions retrieved successfully.",
                walletTransactionService.getStudentTransactions(userId, filter, pageable));
    }

    /** UC-17 alternative flow 6a: detail of one transaction with its related order/refund. */
    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "Chi tiết một giao dịch ví của học viên")
    public ApiResponse<WalletTransactionDetailResponse> getTransactionDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transactionId) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Wallet transaction retrieved successfully.",
                walletTransactionService.getStudentTransactionDetail(userId, transactionId));
    }

}
